package com.example.admission.volunteer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 志愿项实体，对应 volunteer_item 表.
 * 唯一约束: (formId, planId) 和 (formId, sortOrder).
 */
@Data
@TableName("volunteer_item")
public class VolunteerItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属志愿表ID */
    private Long formId;

    /** 招生计划ID */
    private Long planId;

    private Long schoolId;
    private String schoolName;
    private String schoolCode;
    private String majorName;
    private String majorCode;
    private String province;
    private String city;
    private String schoolType;
    private String enrollmentType;
    private java.math.BigDecimal probability;
    private String label;
    private Integer planCount;
    private BigDecimal tuition;
    private String subjectRequirementText;
    private String planStatus;
    private Integer lastYearMinRank;
    private Integer twoYearMinRank;
    private Integer threeYearMinRank;
    private Integer predictedRank;

    /** 排序序号（1-N 连续） */
    private Integer sortOrder;

    /** 备注（最长200字符） */
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final int MAX_NOTE_LENGTH = 200;
}
