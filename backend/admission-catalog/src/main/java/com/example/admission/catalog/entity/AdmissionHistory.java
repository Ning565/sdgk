package com.example.admission.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史录取数据实体，对应 admission_history 表.
 *
 * @author admission-system
 */
@Data
@TableName("admission_history")
public class AdmissionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_series_id")
    private Long planSeriesId;

    @TableField("year")
    private Integer year;

    @TableField("plan_count")
    private Integer planCount;

    @TableField("min_score")
    private BigDecimal minScore;

    @TableField("min_rank")
    private Integer minRank;

    @TableField("avg_score")
    private BigDecimal avgScore;

    @TableField("avg_rank")
    private Integer avgRank;

    @TableField("max_score")
    private BigDecimal maxScore;

    @TableField("max_rank")
    private Integer maxRank;

    @TableField("admission_batch")
    private String admissionBatch;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
