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
 * 缺少保底检查 → WARNING.
 * 志愿表中没有"保"等级的志愿项.
 */
@Component
public class NoSafetyRule implements VolunteerCheckRule {

    public static final String CODE = "NO_SAFETY";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.WARNING; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "志愿表中缺少'保'等级的志愿项，存在滑档风险";
    }

    @Override
    public String suggestion() {
        return "建议至少添加2-3个录取概率较高的'保'等级专业作为安全垫";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getItems() != null && !ctx.getItems().isEmpty()
                && ctx.getPredictionLabelMap() != null;
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        return null; // 批量检测
    }

    /**
     * 检测是否缺少保底.
     */
    public VolunteerCheckIssue detectIfNoSafety(VolunteerCheckContext ctx) {
        Map<Long, String> labelMap = ctx.getPredictionLabelMap();
        if (labelMap == null || labelMap.isEmpty()) return null;

        boolean hasSafety = ctx.getItems().stream()
                .anyMatch(item -> "保".equals(labelMap.get(item.getPlanId())));

        if (!hasSafety && !ctx.getItems().isEmpty()) {
            VolunteerCheckIssue issue = new VolunteerCheckIssue();
            issue.setItemId(null);
            issue.setPlanId(null);
            issue.setSortOrder(null);
            issue.setRuleCode(CODE);
            issue.setLevel(CheckLevel.WARNING.name());
            issue.setMessage("志愿表中缺少'保'等级的志愿项，建议添加");
            issue.setSuggestion(suggestion());
            return issue;
        }
        return null;
    }
}
