package com.example.admission.volunteercheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿检查问题记录，对应 volunteer_check_issue 表.
 */
@Data
@TableName("volunteer_check_issue")
public class VolunteerCheckIssue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属检查运行ID */
    private Long checkRunId;

    /** 志愿表ID */
    private Long formId;

    /** 志愿项ID */
    private Long itemId;

    /** 招生计划ID */
    private Long planId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 规则编码 */
    private String ruleCode;

    /** 问题级别: ERROR / WARNING / INFO */
    private String level;

    /** 问题描述 */
    private String message;

    /** 修复建议 */
    private String suggestion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
