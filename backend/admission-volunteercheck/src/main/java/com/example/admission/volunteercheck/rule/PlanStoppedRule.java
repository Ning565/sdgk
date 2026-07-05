package com.example.admission.volunteercheck.rule;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import org.springframework.stereotype.Component;

/**
 * 计划停止/撤销检查 → ERROR.
 * 招生计划已停止招生或已撤销.
 */
@Component
public class PlanStoppedRule implements VolunteerCheckRule {

    public static final String CODE = "PLAN_STOPPED";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.ERROR; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "该招生计划已停止招生成已撤销，无法填报";
    }

    @Override
    public String suggestion() {
        return "请移除该志愿项，替换为其他正常招生的专业";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getPlanMap() != null && !ctx.getPlanMap().isEmpty();
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        if (plan == null) return null;

        // 状态为 STOPPED / CANCELLED / DELETED 等不可用状态
        String status = plan.getPlanStatus();
        if (status != null && (status.equals("STOPPED") || status.equals("CANCELLED")
                || status.equals("INACTIVE") || status.equals("REVOKED"))) {
            VolunteerCheckIssue issue = new VolunteerCheckIssue();
            issue.setItemId(item.getId());
            issue.setPlanId(item.getPlanId());
            issue.setSortOrder(item.getSortOrder());
            issue.setRuleCode(CODE);
            issue.setLevel(CheckLevel.ERROR.name());
            issue.setMessage("招生计划状态异常: " + status);
            issue.setSuggestion(suggestion());
            return issue;
        }
        return null;
    }
}
