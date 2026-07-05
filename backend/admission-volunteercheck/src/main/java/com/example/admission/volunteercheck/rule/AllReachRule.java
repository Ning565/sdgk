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
 * 全部为冲检查 → WARNING.
 * 所有志愿项均为"冲"等级时发出警告.
 */
@Component
public class AllReachRule implements VolunteerCheckRule {

    public static final String CODE = "ALL_REACH";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.WARNING; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "所有志愿项均为'冲'等级，风险过高";
    }

    @Override
    public String suggestion() {
        return "建议增加'稳'和'保'等级的志愿项，形成合理的梯度布局";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getItems() != null && !ctx.getItems().isEmpty()
                && ctx.getPredictionLabelMap() != null;
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        // 此规则需要全局视角，通过 detectIfAllReach 批量处理
        return null;
    }

    /**
     * 检测是否全部为"冲".
     */
    public List<VolunteerCheckIssue> detectIfAllReach(VolunteerCheckContext ctx) {
        Map<Long, String> labelMap = ctx.getPredictionLabelMap();
        if (labelMap == null || labelMap.isEmpty()) return Collections.emptyList();

        boolean allChong = ctx.getItems().stream()
                .allMatch(item -> "冲".equals(labelMap.get(item.getPlanId())));

        if (allChong && !ctx.getItems().isEmpty()) {
            List<VolunteerCheckIssue> issues = new ArrayList<>();
            for (VolunteerItem item : ctx.getItems()) {
                VolunteerCheckIssue issue = new VolunteerCheckIssue();
                issue.setItemId(item.getId());
                issue.setPlanId(item.getPlanId());
                issue.setSortOrder(item.getSortOrder());
                issue.setRuleCode(CODE);
                issue.setLevel(CheckLevel.WARNING.name());
                issue.setMessage("全部志愿项均为'冲'等级，缺乏稳和保的梯度");
                issue.setSuggestion(suggestion());
                issues.add(issue);
            }
            return issues;
        }
        return Collections.emptyList();
    }
}
