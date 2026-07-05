package com.example.admission.volunteercheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单项检查问题响应.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckIssueResponse {

    /** 问题ID */
    private Long id;

    /** 志愿项ID */
    private Long itemId;

    /** 招生计划ID */
    private Long planId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 学校名称 */
    private String schoolName;

    /** 专业名称 */
    private String majorName;

    /** 规则编码 */
    private String ruleCode;

    /** 问题级别: ERROR / WARNING / INFO */
    private String level;

    /** 问题描述 */
    private String message;

    /** 修复建议 */
    private String suggestion;
}
