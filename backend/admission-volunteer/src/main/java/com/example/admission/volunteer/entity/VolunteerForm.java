package com.example.admission.volunteer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿表实体，对应 volunteer_form 表.
 */
@Data
@TableName("volunteer_form")
public class VolunteerForm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 高考年份 */
    private Integer year;

    /** 志愿表名称 */
    private String name;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 志愿项数量 */
    private Integer itemCount;

    /** 状态: ACTIVE / ARCHIVED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
}
