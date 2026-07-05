package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准专业实体，对应 standard_major 表.
 *
 * @author admission-system
 */
@Data
@TableName("standard_major")
public class StandardMajor {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String majorCode;

    @TableField("name")
    private String majorName;

    @TableField("category_code")
    private String categoryCode;

    @TableField("category_name")
    private String categoryName;

    @TableField("subcategory_code")
    private String subcategoryCode;

    @TableField("subcategory_name")
    private String subcategoryName;

    @TableField("education_level")
    private String educationLevel;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
