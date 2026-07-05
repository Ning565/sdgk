package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专业外链实体，对应 major_link 表.
 *
 * @author admission-system
 */
@Data
@TableName("major_link")
public class MajorLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_series_id")
    private Long majorId;

    @TableField("link_type")
    private String linkType;

    @TableField("title")
    private String title;

    @TableField("url")
    private String url;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
