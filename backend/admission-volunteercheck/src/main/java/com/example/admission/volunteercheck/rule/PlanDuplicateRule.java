package com.example.admission.volunteercheck.rule;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 重复planId检查 → ERROR.
 * 同一计划不应重复添加.
 */
@Component
public class PlanDuplicateRule implements VolunteerCheckRule {

    public static final String CODE = "PLAN_DUPLICATE";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.ERROR; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "志愿表中存在重复的招生计划";
    }

    @Override
    public String suggestion() {
        return "请移除重复的志愿项，每个招生计划只需保留一条";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getItems() != null && ctx.getItems().size() > 1;
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        // 此规则在VolunteerCheckService中通过planId计数来批量处理
        // 不在单项评估中处理
        return null;
    }

    /**
     * 批量检测重复.
     */
    public List<VolunteerCheckIssue> detectDuplicates(VolunteerCheckContext ctx) {
        List<VolunteerCheckIssue> issues = new ArrayList<>();
        Map<Long, List<VolunteerItem>> planIdMap = new LinkedHashMap<>();
        for (VolunteerItem item : ctx.getItems()) {
            planIdMap.computeIfAbsent(item.getPlanId(), k -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<Long, List<VolunteerItem>> entry : planIdMap.entrySet()) {
            List<VolunteerItem> duplicates = entry.getValue();
            if (duplicates.size() > 1) {
                // 保留第一个，后续的都标记为重复
                for (int i = 1; i < duplicates.size(); i++) {
                    VolunteerItem dupItem = duplicates.get(i);
                    VolunteerCheckIssue issue = new VolunteerCheckIssue();
                    issue.setItemId(dupItem.getId());
                    issue.setPlanId(dupItem.getPlanId());
                    issue.setSortOrder(dupItem.getSortOrder());
                    issue.setRuleCode(CODE);
                    issue.setLevel(CheckLevel.ERROR.name());
                    issue.setMessage("志愿项 #" + dupItem.getSortOrder() + " 与 #"
                            + duplicates.get(0).getSortOrder() + " 重复");
                    issue.setSuggestion(suggestion());
                    issues.add(issue);
                }
            }
        }
        return issues;
    }
}
