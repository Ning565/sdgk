package com.example.admission.volunteercheck.rule;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 学费超出上限检查 → WARNING.
 * 计划学费超出考生设置的学费上限.
 */
@Component
public class TuitionExceededRule implements VolunteerCheckRule {

    public static final String CODE = "TUITION_EXCEEDED";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.WARNING; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "该专业学费超出您设置的学费上限";
    }

    @Override
    public String suggestion() {
        return "请确认是否接受该学费水平，或调整学费上限设置";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getProfile() != null && ctx.getProfile().getTuitionMax() != null
                && ctx.getProfile().getTuitionMax() > 0;
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        if (plan == null || profile == null) return null;

        Integer tuitionMax = profile.getTuitionMax();
        if (tuitionMax == null || tuitionMax <= 0) return null;

        BigDecimal planTuition = plan.getTuition();
        if (planTuition == null) return null;

        if (planTuition.compareTo(BigDecimal.valueOf(tuitionMax)) > 0) {
            VolunteerCheckIssue issue = new VolunteerCheckIssue();
            issue.setItemId(item.getId());
            issue.setPlanId(item.getPlanId());
            issue.setSortOrder(item.getSortOrder());
            issue.setRuleCode(CODE);
            issue.setLevel(CheckLevel.WARNING.name());
            issue.setMessage("学费" + planTuition + "元/年，超出上限" + tuitionMax + "元/年");
            issue.setSuggestion(suggestion());
            return issue;
        }
        return null;
    }
}
