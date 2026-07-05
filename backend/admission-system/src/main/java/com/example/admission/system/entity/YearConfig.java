package com.example.admission.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年度配置实体，对应 year_config 表.
 */
@Data
@TableName("year_config")
public class YearConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("year")
    private Integer year;

    @TableField("score_min")
    private Integer scoreMin;

    @TableField("score_max")
    private Integer scoreMax;

    @TableField("volunteer_limit")
    private Integer volunteerLimit;

    @TableField("is_open")
    private Integer isOpen;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
