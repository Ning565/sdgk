package com.example.admission.volunteercheck.rule;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.VolunteerCheckContext;
import com.example.admission.volunteercheck.VolunteerCheckRule;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import org.springframework.stereotype.Component;

/**
 * 新增专业/历史不足检查 → INFO.
 * 该专业为新增计划，缺少历史录取数据做参考.
 */
@Component
public class NewPlanRule implements VolunteerCheckRule {

    public static final String CODE = "NEW_PLAN";

    @Override
    public String code() { return CODE; }

    @Override
    public CheckLevel level() { return CheckLevel.INFO; }

    @Override
    public String message(VolunteerItem item, CandidateProfile profile) {
        return "该专业为新增计划或历史数据不足，缺乏往年录取参考";
    }

    @Override
    public String suggestion() {
        return "请结合自身位次和院校整体水平综合判断，谨慎填报";
    }

    @Override
    public boolean supports(VolunteerCheckContext ctx) {
        return ctx.getPlanMap() != null && !ctx.getPlanMap().isEmpty();
    }

    @Override
    public VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                         EnrollmentPlan plan, String planPredictionLabel) {
        if (plan == null) return null;

        // 判断: plan_series_id 为 null 或不存在历史数据 → 新专业
        if (plan.getPlanSeriesId() == null || plan.getPlanSeriesId() <= 0) {
            VolunteerCheckIssue issue = new VolunteerCheckIssue();
            issue.setItemId(item.getId());
            issue.setPlanId(item.getPlanId());
            issue.setSortOrder(item.getSortOrder());
            issue.setRuleCode(CODE);
            issue.setLevel(CheckLevel.INFO.name());
            issue.setMessage("专业\"" + plan.getMajorName() + "\"缺少历史录取数据参考");
            issue.setSuggestion(suggestion());
            return issue;
        }
        return null;
    }
}
