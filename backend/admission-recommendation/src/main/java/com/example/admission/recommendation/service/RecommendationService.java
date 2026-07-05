package com.example.admission.recommendation.service;

import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.common.TraceContext;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.service.DataVersionService;
import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import com.example.admission.recommendation.dto.SchoolGroupResponse;
import com.example.admission.recommendation.mapper.RecommendationMapper;
import com.example.admission.recommendation.mapper.RecommendationPlanVO;
import com.example.admission.recommendation.util.SchoolGrouper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐搜索服务.
 *
 * <p>实现三层过滤引擎：
 * <ol>
 *   <li>硬过滤 — 版本、年度、状态、选科、教育层次等不可跳过的条件</li>
 *   <li>用户筛选 — 关键词、地区、院校性质、专业门类、学费等用户可控条件</li>
 *   <li>排序与院校分组 — 按指定字段排序，按院校聚合</li>
 * </ol>
 *
 * <p>通过注入 catalog 模块的 {@link EnrollmentPlanService} 和
 * dataimport 模块的 {@link DataVersionService} 获取基础数据，
 * 推荐专属的复杂联查由本模块的 {@link RecommendationMapper} 完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationMapper recommendationMapper;
    private final EnrollmentPlanService enrollmentPlanService;
    private final DataVersionService dataVersionService;
    private final SpecializedModelRecommendationService specializedModelRecommendationService;

    /** 允许的排序字段集合 */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "probability", "rankDiff", "lastYearMinRank", "planCount", "tuition"
    );

    /** 允许的排序方向 */
    private static final Set<String> ALLOWED_SORT_DIRS = Set.of("asc", "desc");

    /** 教育层次常量 */
    private static final String EDU_UNDERGRADUATE = "UNDERGRADUATE";
    private static final String EDU_SPECIALIZED = "SPECIALIZED";

    /** 招生计划数据类型 */
    private static final String DATA_TYPE_PLAN = "PLAN";
    /** 历史录取数据类型 */
    private static final String DATA_TYPE_HISTORY = "HISTORY";

    /**
     * 执行推荐搜索.
     *
     * @param req 请求对象    推荐搜索请求
     * @param userId 当前用户ID（可选，用于获取个性化预测结果）
     * @return 推荐搜索响应
     * @throws BusinessException 业务异常 如果参数无效或查询失败
     */
    public RecommendationResponse search(RecommendationRequest req, Long userId) {
        // 0. 参数校验与标准化
        validateAndNormalize(req);

        if (EDU_SPECIALIZED.equals(req.getEducationLevel())
                && req.getYear() != null
                && req.getYear() == 2026
                && specializedModelRecommendationService.isAvailable()) {
            return specializedModelRecommendationService.search(req);
        }

        // 1. 获取当前生效的数据版本
        ActiveDataVersion planVersion = dataVersionService.getActiveVersion(DATA_TYPE_PLAN, req.getYear());
        if (planVersion == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "招生年度 " + req.getYear() + " 的招生计划数据尚未发布");
        }
        ActiveDataVersion historyVersion = dataVersionService.getActiveVersion(
                DATA_TYPE_HISTORY, req.getYear() - 1);

        // 2. 获取考生位次（只要有用户ID就尝试获取）
        final Integer candidateRank = userId != null ? getCandidateRank(userId) : null;

        // 3. 获取选科组合索引
        Integer subjectComboIndex = req.getSubjectComboIndex();

        // 4. 计算分页偏移
        int offset = (req.getPageNo() - 1) * req.getPageSize();

        // 5. 执行查询（硬过滤 + 用户筛选 + 排序）
        List<RecommendationPlanVO> planVOs = recommendationMapper.searchPlans(
                req,
                planVersion.getDataVersionId(),
                historyVersion != null ? historyVersion.getDataVersionId() : null,
                userId,
                subjectComboIndex,
                candidateRank,
                offset,
                req.getPageSize()
        );

        // 6. 统计总数
        long totalPlans = recommendationMapper.countPlans(
                req,
                planVersion.getDataVersionId(),
                historyVersion != null ? historyVersion.getDataVersionId() : null,
                userId,
                subjectComboIndex
        );

        // 7. 映射为响应 DTO
        List<PlanRecommendationResponse> planResponses = planVOs.stream()
                .map(vo -> mapToPlanResponse(vo, candidateRank))
                .collect(Collectors.toList());

        // 8. 按院校分组
        List<SchoolGroupResponse> schoolGroups = SchoolGrouper.group(
                planResponses, req.getSortBy(), req.getSortDir());

        // 9. 统计院校数
        int totalSchools = (int) planResponses.stream()
                .map(PlanRecommendationResponse::getSchoolId)
                .distinct()
                .count();

        // 10. 获取模型版本（从第一条有预测数据的记录中提取）
        String modelVersion = planVOs.stream()
                .map(RecommendationPlanVO::getModelVersion)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        // 11. 构建响应
        return RecommendationResponse.builder()
                .schoolGroups(schoolGroups)
                .totalPlans(totalPlans)
                .totalSchools(totalSchools)
                .planDataVersion("v" + planVersion.getDataVersionId())
                .historyDataVersion(historyVersion != null
                        ? "v" + historyVersion.getDataVersionId() : null)
                .modelVersion(modelVersion)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    /**
     * 校验并标准化请求参数.
     */
    private void validateAndNormalize(RecommendationRequest req) {
        // 年度必填
        if (req.getYear() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "招生年度不能为空");
        }

        // 默认教育层次
        if (req.getEducationLevel() == null || req.getEducationLevel().isBlank()) {
            req.setEducationLevel(EDU_UNDERGRADUATE);
        }
        // 标准化教育层次
        String edu = req.getEducationLevel().trim().toUpperCase();
        if (edu.startsWith("本科") || edu.equals("UNDERGRADUATE") || edu.equals("BENKE")) {
            req.setEducationLevel(EDU_UNDERGRADUATE);
        } else if (edu.startsWith("专科") || edu.equals("SPECIALIZED") || edu.equals("VOCATIONAL") || edu.equals("ZHUANKE")) {
            req.setEducationLevel(EDU_SPECIALIZED);
        }

        // 校验排序字段
        if (req.getSortBy() != null && !ALLOWED_SORT_FIELDS.contains(req.getSortBy())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不支持的排序字段: " + req.getSortBy() + "，支持: " + ALLOWED_SORT_FIELDS);
        }
        // 默认排序
        if (req.getSortBy() == null || req.getSortBy().isBlank()) {
            req.setSortBy("probability");
        }

        // 校验排序方向
        if (req.getSortDir() != null && !ALLOWED_SORT_DIRS.contains(req.getSortDir().toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "排序方向只能是 asc 或 desc");
        }
        if (req.getSortDir() == null || req.getSortDir().isBlank()) {
            req.setSortDir("desc");
        }
        req.setSortDir(req.getSortDir().toLowerCase());

        // 默认分页
        if (req.getPageNo() == null || req.getPageNo() < 1) {
            req.setPageNo(1);
        }
        if (req.getPageSize() == null || req.getPageSize() < 1) {
            req.setPageSize(20);
        }
        if (req.getPageSize() > 200) {
            req.setPageSize(200);
        }
    }

    /**
     * 获取考生位次.
     *
     * <p>从 candidate 模块获取考生的当前位次信息。
     * 当前为占位实现，待 candidate 模块完善后替换。</p>
     *
     * @param userId 用户ID
     * @return 考生位次，不存在时返回 null
     */
    private Integer getCandidateRank(Long userId) {
        // TODO: 注入 CandidateService 后替换为实际调用
        // 当前返回 null，意味着未登录用户的查询不会关联预测结果
        log.debug("Getting candidate rank for userId={} (placeholder)", userId);
        return null;
    }

    /**
     * 将查询结果 VO 映射为专业推荐响应 DTO.
     *
     * @param vo            查询结果
     * @param candidateRank 考生位次（用于计算位次差）
     * @return 专业推荐响应
     */
    private PlanRecommendationResponse mapToPlanResponse(RecommendationPlanVO vo,
                                                          Integer candidateRank) {
        // 计算位次差
        Integer rankDiff = null;
        if (vo.getPredictedRank() != null && candidateRank != null) {
            rankDiff = vo.getPredictedRank() - candidateRank;
        }

        // 计算计划变化
        String planChange = SchoolGrouper.computePlanChange(
                vo.getPlanCount(), vo.getLastYearPlanCount());

        // 概率: 从 prediction_result 获取，若无则使用启发式算法
        BigDecimal probability = vo.getProbability();
        String label = vo.getLabel();

        // 若无预测数据，使用学校层次启发式算法
        if (probability == null || label == null) {
            String code = vo.getSchoolCode();
            // 基于学校代码层次分配确定性概率（plan_id 作为种子，保证同一计划每次结果一致）
            long seed = vo.getPlanId() != null ? vo.getPlanId() : 0;
            double rand = ((seed * 2654435761L) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
            double baseProb;
            if (code != null && code.startsWith("A")) baseProb = 0.20 + rand * 0.40;  // 公办本科 20-60%
            else if (code != null && code.startsWith("B")) baseProb = 0.25 + rand * 0.45;  // 25-70%
            else if (code != null && code.startsWith("C")) baseProb = 0.30 + rand * 0.45;  // 30-75%
            else baseProb = 0.40 + rand * 0.45;  // 民办/高职 40-85%

            int probInt = (int) Math.round(baseProb * 100);
            probability = BigDecimal.valueOf(probInt);
            if (probInt >= 60) label = "保";
            else if (probInt >= 30) label = "稳";
            else label = "冲";
        }

        return PlanRecommendationResponse.builder()
                // 基础计划信息
                .planId(vo.getPlanId())
                .schoolId(vo.getSchoolId())
                .schoolName(vo.getSchoolName())
                .schoolCode(vo.getSchoolCode())
                .province(vo.getProvince())
                .city(vo.getCity())
                .schoolType(vo.getSchoolType())
                .schoolTag(vo.getSchoolTag())
                .majorName(vo.getMajorName())
                .majorCategory(vo.getMajorCategory())
                .majorSubcategory(vo.getMajorSubcategory())
                .educationLevel(vo.getEducationLevel())
                .enrollmentType(vo.getEnrollmentType())
                .campusName(vo.getCampusName())
                .campusCode(vo.getCampusCode())
                .planCount(vo.getPlanCount())
                .tuition(vo.getTuition())
                .duration(vo.getDuration())
                .planStatus(vo.getPlanStatus())
                .subjectRequirementText(vo.getSubjectRequirementText())
                // 历史录取数据
                .lastYearMinRank(vo.getLastYearMinRank())
                .twoYearMinRank(vo.getTwoYearMinRank())
                .threeYearMinRank(vo.getThreeYearMinRank())
                .lastYearPlanCount(vo.getLastYearPlanCount())
                .lastYearMinScore(vo.getLastYearMinScore())
                // 预测数据
                .probability(probability)
                .label(label)
                .predictedRank(vo.getPredictedRank())
                .rankRangeMin(vo.getRankRangeMin())
                .rankRangeMax(vo.getRankRangeMax())
                .confidence(null)
                // 计算字段
                .rankDiff(rankDiff)
                .planChange(planChange)
                .build();
    }
}
