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
 * 选科不符检查 → ERROR.
 * 考生的选科组合与招生计划的选科要求不匹配.
 */
@Component
public class SubjectMismatchRule implements VolunteerCheckRule {

    public static final String CODE = "SUBJECT_MISMATCH";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.ERROR; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "选科要求不符，考生的选科组合不满足该专业的选科要求";
    }

    @Override
    public String suggestion() {
        return "请选择与自身选科组合匹配的专业，或查看专业具体选科要求";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getProfile() != null && ctx.getProfile().getSubjects() != null;
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        if (plan == null || profile == null) return null;

        String subjectRequirement = plan.getSubjectRequirementText();
        if (subjectRequirement == null || subjectRequirement.isBlank()) return null;

        List<String> candidateSubjects = profile.getSubjects();
        if (candidateSubjects.isEmpty()) return null;

        // 检查考生选科是否包含所有必选科目
        String req = subjectRequirement.replace(" ", "").toUpperCase();
        List<String> upperSubjects = candidateSubjects.stream()
                .map(String::toUpperCase).toList();

        // 简单解析: 检查要求中的科目是否都在考生选科中
        boolean mismatch = false;
        if (req.contains("物理") && !upperSubjects.contains("物理")) mismatch = true;
        else if (req.contains("化学") && !upperSubjects.contains("化学")) mismatch = true;
        else if (req.contains("生物") && !upperSubjects.contains("生物")) mismatch = true;
        else if (req.contains("历史") && !upperSubjects.contains("历史")) mismatch = true;
        else if (req.contains("地理") && !upperSubjects.contains("地理")) mismatch = true;
        else if (req.contains("政治") && !upperSubjects.contains("政治")) mismatch = true;

        if (mismatch) {
            return buildIssue(item, plan);
        }
        return null;
    }

    private VolunteerCheckIssue buildIssue(VolunteerItem item, EnrollmentPlan plan) {
        VolunteerCheckIssue issue = new VolunteerCheckIssue();
        issue.setItemId(item.getId());
        issue.setPlanId(item.getPlanId());
        issue.setSortOrder(item.getSortOrder());
        issue.setRuleCode(CODE);
        issue.setLevel(CheckLevel.ERROR.name());
        issue.setMessage("选科不符: 要求" + plan.getSubjectRequirementText());
        issue.setSuggestion(suggestion());
        return issue;
    }
}
