package com.example.admission.candidate.service;

import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.candidate.dto.CandidateProfileRequest;
import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.candidate.mapper.CandidateProfileMapper;
import com.example.admission.candidate.util.SubjectComboUtil;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考生档案服务.
 *
 * <p>管理考生的个人档案（分数、位次、选科、偏好等），
 * 支持创建、更新、查询操作。
 * 同一用户同一年度只能有一份档案，重复提交视为更新。</p>
 *
 * <p><b>位次管理规则：</b>
 * <ul>
 *   <li>score_table(自动匹配): 从一分一段表自动匹配位次</li>
 *   <li>manual(手动填写): 用户手动填入的位次</li>
 *   <li>分数变更时自动清除手动位次并重新匹配</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateProfileMapper candidateProfileMapper;
    private final AuthService authService;
    private final ScoreRankService scoreRankService;

    /**
     * 查询当前用户指定年份的考生档案.
     *
     * <p>从 {@link AuthService} 获取当前登录用户ID，
     * 如果用户未登录则返回 null。</p>
     *
     * @param year 年份 高考年份
     * @return CandidateProfile 或 null（未登录/档案不存在）
     */
    public CandidateProfile getProfile(Integer year) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            log.debug("User not logged in, returning null for profile query");
            return null;
        }
        CandidateProfile profile = candidateProfileMapper.selectByUserIdAndYear(userId, year);
        if (profile == null) {
            log.debug("No profile found for userId={}, year={}", userId, year);
        } else {
            log.debug("Profile found: id={}, userId={}, year={}, score={}, rank={}",
                    profile.getId(), userId, year, profile.getScore(), profile.getRank());
        }
        return profile;
    }

    /**
     * 创建或更新考生档案.
     *
     * <h3>处理流程：</h3>
     * <ol>
     *   <li>校验：subjects 必须为3门，rank > 0</li>
     *   <li>同一用户同一年度只能有一份档案，已存在则更新</li>
     *   <li>计算 subjectComboIndex（调用 SubjectComboUtil）</li>
     *   <li>如果 rankSource=AUTO，始终从一分一段表按当前分数匹配位次</li>
     *   <li>如果 rankSource=MANUAL，保留用户手动填入的位次</li>
     * </ol>
     *
     * @param req 请求对象uest HTTP 请求 考生档案请求 DTO
     * @return 保存后的 CandidateProfile
     * @throws BusinessException 业务异常 如果数据校验失败
     */
    @Transactional
    public CandidateProfile saveOrUpdate(CandidateProfileRequest request) {
        // 1. 获取当前用户
        UserAccount user = authService.checkLogin();
        Long userId = user.getId();

        // 2. 基本校验
        validateRequest(request);

        // 3. 校验选科组合有效性
        int comboIndex = SubjectComboUtil.getComboIndex(request.getSubjects());
        if (comboIndex < 0) {
            log.warn("Invalid subject combo: userId={}, subjects={}", userId, request.getSubjects());
            throw new BusinessException(ErrorCode.PARAM_ERROR, "选科组合无效，必须为山东6选3的20种有效组合之一");
        }

        // 4. 查询是否已存在（同一用户同一年度）
        CandidateProfile existing = candidateProfileMapper.selectByUserIdAndYear(userId, request.getYear());
        boolean isUpdate = (existing != null);

        CandidateProfile profile;
        if (isUpdate) {
            profile = existing;
            log.info("Updating existing profile: id={}, userId={}, year={}", profile.getId(), userId, request.getYear());
        } else {
            profile = new CandidateProfile();
            profile.setUserId(userId);
            profile.setYear(request.getYear());
            log.info("Creating new profile: userId={}, year={}", userId, request.getYear());
        }

        // 6. 填充字段
        profile.setScore(request.getScore());
        profile.setSubjects(request.getSubjects()); // 内部自动计算 subjectComboIndex
        profile.setSubjectComboIndex(comboIndex);
        profile.setEducationLevel(defaultIfNull(request.getEducationLevel(), CandidateProfile.EDU_UNLIMITED));
        profile.setPreferredRegions(request.getPreferredRegions());
        profile.setPreferredMajors(request.getPreferredMajors());
        profile.setExcludedMajors(request.getExcludedMajors());
        profile.setTuitionMax(request.getTuitionMax());
        profile.setSchoolNature(defaultIfNull(request.getSchoolNature(), CandidateProfile.SCHOOL_UNLIMITED));
        profile.setAcceptJointProgram(request.getAcceptJointProgram());
        profile.setCooperationTypes(request.getCooperationTypes());
        profile.setRemark(request.getRemark());

        // 7. 位次处理逻辑
        String rankSource = request.getRankSource();
        profile.setRankSource(rankSource);

        if (CandidateProfile.RANK_SOURCE_AUTO.equals(rankSource)) {
            // AUTO 模式：每次保存都按当前分数重新匹配，避免修改资料后继续沿用旧位次。
            try {
                var resolved = scoreRankService.resolve(request.getYear(), request.getScore());
                profile.setRank(resolved.getCumulativeCount());
                log.info("Auto rank resolved: year={}, score={}, rank={}",
                        request.getYear(), request.getScore(), resolved.getCumulativeCount());
            } catch (BusinessException e) {
                if (ErrorCode.SCORE_NOT_FOUND.equals(e.getErrorCode())) {
                    // 一分一段表中未找到该分数，回退为手动模式
                    log.warn("Score not found in rank table, falling back to manual rank: year={}, score={}",
                            request.getYear(), request.getScore());
                    profile.setRankSource(CandidateProfile.RANK_SOURCE_MANUAL);
                    profile.setRank(request.getRank());
                } else {
                    throw e;
                }
            }
        } else {
            // MANUAL 模式：直接使用用户请求中的位次
            profile.setRank(request.getRank());
        }

        profile.setUpdatedAt(LocalDateTime.now());

        // 8. 持久化
        if (isUpdate) {
            candidateProfileMapper.updateById(profile);
        } else {
            profile.setCreatedAt(LocalDateTime.now());
            candidateProfileMapper.insert(profile);
        }

        log.info("Profile saved: id={}, userId={}, year={}, score={}, rank={}, rankSource={}, comboIndex={}",
                profile.getId(), userId, request.getYear(),
                profile.getScore(), profile.getRank(), profile.getRankSource(), comboIndex);

        return profile;
    }

    /**
     * 根据科目列表获取选科组合索引.
     *
     * <p>包装 {@link SubjectComboUtil#getComboIndex(List)}，
     * 方便外部调用者无需直接依赖工具类。</p>
     *
     * @param subjects 3门科目名称列表
     * @return 组合索引 (0-19)，无效组合返回 -1
     */
    public int getSubjectComboIndex(List<String> subjects) {
        return SubjectComboUtil.getComboIndex(subjects);
    }

    // --- Private helpers ---

    /**
     * 校验请求参数.
     */
    private void validateRequest(CandidateProfileRequest request) {
        // subjects 必须为3门
        List<String> subjects = request.getSubjects();
        if (subjects == null || subjects.size() != 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "选科科目必须为3门");
        }

        // rank 必须大于 0
        if (request.getRank() == null || request.getRank() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "位次必须为正数");
        }

        // year 必填
        if (request.getYear() == null || request.getYear() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "高考年份无效");
        }

        // score 基本校验
        if (request.getScore() == null || request.getScore() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "高考分数无效");
        }

        // rankSource 校验
        String rankSource = request.getRankSource();
        if (rankSource == null || rankSource.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "位次来源不能为空");
        }
        if (!CandidateProfile.RANK_SOURCE_AUTO.equals(rankSource)
                && !CandidateProfile.RANK_SOURCE_MANUAL.equals(rankSource)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "位次来源无效，必须为 AUTO 或 MANUAL");
        }
    }

    private static String defaultIfNull(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
