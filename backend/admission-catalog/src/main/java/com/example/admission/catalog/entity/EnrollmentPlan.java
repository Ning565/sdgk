package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招生计划实体，对应 enrollment_plan 表.
 *
 * @author admission-system
 */
@Data
@TableName("enrollment_plan")
public class EnrollmentPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_series_id")
    private Long planSeriesId;

    @TableField("data_version_id")
    private Long dataVersionId;

    @TableField("year")
    private Integer year;

    @TableField("school_id")
    private Long schoolId;

    @TableField("school_code")
    private String schoolCode;

    /** 非DB字段，通过JOIN school表获取 */
    @TableField(exist = false)
    private String schoolName;

    @TableField("major_code")
    private String majorCode;

    @TableField("major_name")
    private String majorName;

    @TableField("standard_major_code")
    private String standardMajorCode;

    /** 非DB字段，通过JOIN standard_major表获取 */
    @TableField(exist = false)
    private String majorCategory;

    @TableField("campus_code")
    private String campusCode;

    @TableField("campus_name")
    private String campusName;

    @TableField("enrollment_type_code")
    private String enrollmentType;

    @TableField("eligible_subject_bitmap")
    private Long eligibleSubjectBitmap;

    @TableField("subject_rule_status")
    private String subjectRuleStatus;

    @TableField("last_year_min_rank")
    private Integer lastYearMinRank;

    @TableField("two_year_min_rank")
    private Integer twoYearMinRank;

    @TableField("three_year_min_rank")
    private Integer threeYearMinRank;

    @TableField("remark")
    private String remark;

    @TableField("education_level")
    private String educationLevel;

    @TableField("plan_count")
    private Integer planCount;

    @TableField("tuition")
    private BigDecimal tuition;

    @TableField("duration")
    private Integer duration;

    @TableField("subject_requirement_text")
    private String subjectRequirementText;

    @TableField("subject_rule_json")
    private String subjectRuleJson;

    @TableField("plan_status")
    private String planStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
