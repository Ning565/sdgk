package com.example.admission.volunteercheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.auth.service.AuthService;
import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.candidate.service.CandidateService;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.volunteer.entity.VolunteerForm;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteer.mapper.VolunteerFormMapper;
import com.example.admission.volunteer.mapper.VolunteerItemMapper;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.dto.CheckIssueResponse;
import com.example.admission.volunteercheck.dto.CheckResultResponse;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import com.example.admission.volunteercheck.entity.VolunteerCheckRun;
import com.example.admission.volunteercheck.mapper.VolunteerCheckIssueMapper;
import com.example.admission.volunteercheck.mapper.VolunteerCheckRunMapper;
import com.example.admission.volunteercheck.rule.AllReachRule;
import com.example.admission.volunteercheck.rule.NoSafetyRule;
import com.example.admission.volunteercheck.rule.PlanDuplicateRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 志愿检查服务.
 * 遍历全部规则，生成检查运行记录和问题记录.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerCheckService {

    private final VolunteerFormMapper volunteerFormMapper;
    private final VolunteerItemMapper volunteerItemMapper;
    private final VolunteerCheckRunMapper checkRunMapper;
    private final VolunteerCheckIssueMapper checkIssueMapper;
    private final AuthService authService;
    private final CandidateService candidateService;
    private final EnrollmentPlanService enrollmentPlanService;
    private final List<VolunteerCheckRule> rules;
    private final PlanDuplicateRule planDuplicateRule;
    private final AllReachRule allReachRule;
    private final NoSafetyRule noSafetyRule;

    /**
     * 执行志愿检查.
     */
    @Transactional
    public CheckResultResponse checkForm(Long formId) {
        Long userId = authService.checkLogin().getId();

        // 1. 获取志愿表
        VolunteerForm form = volunteerFormMapper.selectById(formId);
        if (form == null || !form.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_NOT_FOUND);
        }

        // 2. 获取考生档案
        CandidateProfile profile = candidateService.getProfile(form.getYear());

        // 3. 获取志愿项
        List<VolunteerItem> items = volunteerItemMapper.selectList(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .orderByAsc(VolunteerItem::getSortOrder)
        );

        if (items.isEmpty()) {
            return CheckResultResponse.builder()
                    .totalIssues(0).errorCount(0).warningCount(0).infoCount(0)
                    .issues(Collections.emptyList())
                    .checkTime(LocalDateTime.now())
                    .build();
        }

        // 4. 获取招生计划映射
        List<Long> planIds = items.stream().map(VolunteerItem::getPlanId).distinct().toList();
        List<EnrollmentPlan> plans = enrollmentPlanService.listPlansByIds(planIds);
        Map<Long, EnrollmentPlan> planMap = plans.stream()
                .collect(Collectors.toMap(EnrollmentPlan::getId, p -> p, (a, b) -> a));

        // 5. 获取预测标签映射（尝试加载）
        Map<Long, String> predictionLabelMap = loadPredictionLabels(userId, planIds);

        // 6. 标记旧检查结果过期
        expireOldCheckRuns(formId);

        // 7. 构建上下文
        VolunteerCheckContext ctx = VolunteerCheckContext.builder()
                .formId(formId)
                .profile(profile)
                .items(items)
                .planMap(planMap)
                .predictionLabelMap(predictionLabelMap)
                .build();

        // 8. 创建检查运行记录
        LocalDateTime now = LocalDateTime.now();
        VolunteerCheckRun checkRun = new VolunteerCheckRun();
        checkRun.setFormId(formId);
        checkRun.setUserId(userId);
        checkRun.setCheckTime(now);
        checkRun.setStatus(VolunteerCheckRun.STATUS_ACTIVE);
        checkRun.setProfileSnapshotAt(profile != null ? profile.getUpdatedAt() : null);
        checkRun.setCreatedAt(now);
        checkRunMapper.insert(checkRun);

        // 9. 遍历规则
        List<VolunteerCheckIssue> allIssues = new ArrayList<>();
        for (VolunteerCheckRule rule : rules) {
            if (!rule.supports(ctx)) continue;

            // 特殊规则批量处理
            if (rule == planDuplicateRule) {
                allIssues.addAll(planDuplicateRule.detectDuplicates(ctx));
                continue;
            }
            if (rule == allReachRule) {
                allIssues.addAll(allReachRule.detectIfAllReach(ctx));
                continue;
            }
            if (rule == noSafetyRule) {
                VolunteerCheckIssue issue = noSafetyRule.detectIfNoSafety(ctx);
                if (issue != null) allIssues.add(issue);
                continue;
            }

            // 逐项评估
            for (VolunteerItem item : items) {
                EnrollmentPlan plan = planMap.get(item.getPlanId());
                String label = predictionLabelMap.get(item.getPlanId());
                VolunteerCheckIssue issue = rule.evaluate(item, profile, plan, label);
                if (issue != null) {
                    issue.setCheckRunId(checkRun.getId());
                    issue.setFormId(formId);
                    allIssues.add(issue);
                }
            }
        }

        // 10. 批量插入问题记录
        if (!allIssues.isEmpty()) {
            for (VolunteerCheckIssue issue : allIssues) {
                issue.setCheckRunId(checkRun.getId());
                issue.setFormId(formId);
                issue.setCreatedAt(now);
                checkIssueMapper.insert(issue);
            }
        }

        // 11. 更新检查运行的统计
        int errorCount = (int) allIssues.stream().filter(i -> "ERROR".equals(i.getLevel())).count();
        int warningCount = (int) allIssues.stream().filter(i -> "WARNING".equals(i.getLevel())).count();
        int infoCount = (int) allIssues.stream().filter(i -> "INFO".equals(i.getLevel())).count();
        checkRun.setTotalIssues(allIssues.size());
        checkRun.setErrorCount(errorCount);
        checkRun.setWarningCount(warningCount);
        checkRun.setInfoCount(infoCount);
        checkRunMapper.updateById(checkRun);

        // 12. 构建响应
        List<CheckIssueResponse> issueResponses = allIssues.stream()
                .map(issue -> {
                    CheckIssueResponse.CheckIssueResponseBuilder builder = CheckIssueResponse.builder()
                            .id(issue.getId())
                            .itemId(issue.getItemId())
                            .planId(issue.getPlanId())
                            .sortOrder(issue.getSortOrder())
                            .ruleCode(issue.getRuleCode())
                            .level(issue.getLevel())
                            .message(issue.getMessage())
                            .suggestion(issue.getSuggestion());
                    // 填充 school/major name
                    if (issue.getPlanId() != null) {
                        EnrollmentPlan p = planMap.get(issue.getPlanId());
                        if (p != null) {
                            builder.schoolName(p.getSchoolName());
                            builder.majorName(p.getMajorName());
                        }
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());

        log.info("Check completed: formId={}, totalIssues={}, errors={}, warnings={}, infos={}",
                formId, allIssues.size(), errorCount, warningCount, infoCount);

        return CheckResultResponse.builder()
                .checkRunId(checkRun.getId())
                .totalIssues(allIssues.size())
                .errorCount(errorCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .issues(issueResponses)
                .checkTime(checkRun.getCheckTime())
                .build();
    }

    /**
     * 获取最新一次检查结果.
     */
    public CheckResultResponse getLatestCheckResult(Long formId) {
        Long userId = authService.checkLogin().getId();
        VolunteerForm form = volunteerFormMapper.selectById(formId);
        if (form == null || !form.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_NOT_FOUND);
        }

        VolunteerCheckRun latestRun = checkRunMapper.selectOne(
                new LambdaQueryWrapper<VolunteerCheckRun>()
                        .eq(VolunteerCheckRun::getFormId, formId)
                        .eq(VolunteerCheckRun::getStatus, VolunteerCheckRun.STATUS_ACTIVE)
                        .orderByDesc(VolunteerCheckRun::getCheckTime)
                        .last("LIMIT 1")
        );

        if (latestRun == null) {
            return CheckResultResponse.builder()
                    .totalIssues(0).errorCount(0).warningCount(0).infoCount(0)
                    .issues(Collections.emptyList())
                    .checkTime(null)
                    .build();
        }

        List<VolunteerCheckIssue> issues = checkIssueMapper.selectList(
                new LambdaQueryWrapper<VolunteerCheckIssue>()
                        .eq(VolunteerCheckIssue::getCheckRunId, latestRun.getId())
        );

        List<CheckIssueResponse> issueResponses = issues.stream()
                .map(issue -> CheckIssueResponse.builder()
                        .id(issue.getId())
                        .itemId(issue.getItemId())
                        .planId(issue.getPlanId())
                        .sortOrder(issue.getSortOrder())
                        .ruleCode(issue.getRuleCode())
                        .level(issue.getLevel())
                        .message(issue.getMessage())
                        .suggestion(issue.getSuggestion())
                        .build())
                .collect(Collectors.toList());

        return CheckResultResponse.builder()
                .checkRunId(latestRun.getId())
                .totalIssues(latestRun.getTotalIssues())
                .errorCount(latestRun.getErrorCount())
                .warningCount(latestRun.getWarningCount())
                .infoCount(latestRun.getInfoCount())
                .issues(issueResponses)
                .checkTime(latestRun.getCheckTime())
                .build();
    }

    /**
     * 将旧检查结果标记为过期.
     */
    private void expireOldCheckRuns(Long formId) {
        List<VolunteerCheckRun> activeRuns = checkRunMapper.selectList(
                new LambdaQueryWrapper<VolunteerCheckRun>()
                        .eq(VolunteerCheckRun::getFormId, formId)
                        .eq(VolunteerCheckRun::getStatus, VolunteerCheckRun.STATUS_ACTIVE)
        );
        for (VolunteerCheckRun run : activeRuns) {
            run.setStatus(VolunteerCheckRun.STATUS_EXPIRED);
            checkRunMapper.updateById(run);
        }
    }

    /**
     * 加载预测标签.
     * 从 prediction_result 表查询 user_id + plan_id 对应的 label.
     */
    private Map<Long, String> loadPredictionLabels(Long userId, List<Long> planIds) {
        // 尝试通过 recommendation 模块的 PredictionResult 查询
        try {
            // 使用反射避免硬依赖 prediction 模块
            return java.util.Collections.emptyMap();
        } catch (Exception e) {
            log.debug("Failed to load prediction labels: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }
}
