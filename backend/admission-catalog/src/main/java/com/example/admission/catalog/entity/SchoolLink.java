package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 院校外链实体，对应 school_link 表.
 *
 * @author admission-system
 */
@Data
@TableName("school_link")
public class SchoolLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("school_id")
    private Long schoolId;

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
