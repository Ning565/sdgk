package com.example.admission.volunteercheck.rule;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 命中排除专业检查 → WARNING.
 * 志愿项的专业在考生排除专业列表中.
 */
@Component
public class ExcludedMajorRule implements VolunteerCheckRule {

    public static final String CODE = "EXCLUDED_MAJOR";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.WARNING; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "该专业在您设置的排除专业列表中";
    }

    @Override
    public String suggestion() {
        return "该专业已被您标记为排除，如确认可接受可忽略此提示";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getProfile() != null && ctx.getProfile().getExcludedMajors() != null
                && !ctx.getProfile().getExcludedMajors().isEmpty();
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        if (plan == null || profile == null) return null;

        List<String> excludedMajors = profile.getExcludedMajors();
        if (excludedMajors.isEmpty()) return null;

        String majorName = plan.getMajorName();
        if (majorName == null) return null;

        // 检查专业名称或专业类别是否在排除列表中
        for (String excluded : excludedMajors) {
            if (majorName.contains(excluded)
                    || (plan.getMajorCategory() != null && plan.getMajorCategory().contains(excluded))) {
                VolunteerCheckIssue issue = new VolunteerCheckIssue();
                issue.setItemId(item.getId());
                issue.setPlanId(item.getPlanId());
                issue.setSortOrder(item.getSortOrder());
                issue.setRuleCode(CODE);
                issue.setLevel(CheckLevel.WARNING.name());
                issue.setMessage("专业\"" + majorName + "\"匹配排除条件: " + excluded);
                issue.setSuggestion(suggestion());
                return issue;
            }
        }
        return null;
    }
}
