package com.example.admission.catalog.dto;

import com.example.admission.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 招生计划查询条件.
 *
 * @author admission-system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnrollmentPlanQuery extends PageRequest {

    /** 招生年份 */
    private Integer year;

    /** 学历层次: 本科/专科 */
    private String educationLevel;

    /** 关键字搜索 (学校/专业名称模糊匹配) */
    private String keyword;

    /** 所在省份 */
    private String province;

    /** 所在城市 */
    private String city;

    /** 院校类型 */
    private String schoolType;

    /** 院校标签 */
    private String schoolTag;

    /** 专业门类 */
    private String majorCategory;

    /** 专业子类 */
    private String majorSubcategory;

    /** 招生类型 */
    private String enrollmentType;

    /** 校区代码 */
    private String campusCode;

    /** 学费下限 */
    private BigDecimal tuitionMin;

    /** 学费上限 */
    private BigDecimal tuitionMax;

    /** 计划数下限 */
    private Integer planCountMin;

    /** 计划数上限 */
    private Integer planCountMax;

    /** 上一年最低位次上限 */
    private Integer minRankMax;

    /** 计划状态 */
    private String planStatus;

    /** 排序字段 */
    private String sortBy;

    /** 排序方向: asc/desc */
    private String sortDir;
}
