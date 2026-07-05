package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 院校实体，对应 school 表.
 *
 * @author admission-system
 */
@Data
@TableName("school")
public class School {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String schoolCode;

    @TableField("name")
    private String schoolName;

    @TableField("short_name")
    private String shortName;

    @TableField("province")
    private String province;

    @TableField("city")
    private String city;

    @TableField("school_type")
    private String schoolType;

    @TableField("school_tag")
    private String schoolTag;

    @TableField("website")
    private String website;

    @TableField("logo_url")
    private String logoUrl;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("status")
    private String status;
}
