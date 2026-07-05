package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招生计划系列实体，对应 plan_series 表.
 * 用于关联跨年度的同一院校+专业组合.
 *
 * @author admission-system
 */
@Data
@TableName("plan_series")
public class PlanSeries {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("school_code")
    private String schoolCode;

    @TableField("major_code")
    private String majorCode;

    @TableField("enrollment_type")
    private String enrollmentType;

    @TableField("campus_code")
    private String campusCode;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
