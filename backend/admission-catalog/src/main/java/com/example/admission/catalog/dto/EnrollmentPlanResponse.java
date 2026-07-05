package com.example.admission.catalog.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 招生计划响应.
 *
 * @author admission-system
 */
@Data
public class EnrollmentPlanResponse {

    /** 招生计划ID */
    private Long planId;

    /** 招生年份 */
    private Integer year;

    /** 院校ID */
    private Long schoolId;

    /** 院校代码 */
    private String schoolCode;

    /** 院校名称 */
    private String schoolName;

    /** 原始专业代码 */
    private String majorCode;

    /** 原始专业名称 */
    private String majorName;

    /** 标准专业代码 */
    private String standardMajorCode;

    /** 专业门类 */
    private String majorCategory;

    /** 校区代码 */
    private String campusCode;

    /** 校区名称 */
    private String campusName;

    /** 招生类型 */
    private String enrollmentType;

    /** 学历层次 */
    private String educationLevel;

    /** 招生计划数 */
    private Integer planCount;

    /** 学费 */
    private BigDecimal tuition;

    /** 学制(年) */
    private Integer duration;

    /** 选科要求文本描述 */
    private String subjectRequirementText;

    /** 选科规则 JSON */
    private String subjectRuleJson;

    /** 计划状态 */
    private String planStatus;

    /** 数据版本ID */
    private Long dataVersionId;
}
