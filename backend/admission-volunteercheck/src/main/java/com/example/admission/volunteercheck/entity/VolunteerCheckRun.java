package com.example.admission.volunteercheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿检查运行记录，对应 volunteer_check_run 表.
 */
@Data
@TableName("volunteer_check_run")
public class VolunteerCheckRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 志愿表ID */
    private Long formId;

    /** 用户ID */
    private Long userId;

    /** 检查时间 */
    private LocalDateTime checkTime;

    /** 问题总数 */
    private Integer totalIssues;

    /** 错误数 */
    private Integer errorCount;

    /** 警告数 */
    private Integer warningCount;

    /** 信息数 */
    private Integer infoCount;

    /** 考生档案快照版本 (profile.updatedAt) */
    private LocalDateTime profileSnapshotAt;

    /** 数据版本ID */
    private Long dataVersionId;

    /** 状态: ACTIVE / EXPIRED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
}
