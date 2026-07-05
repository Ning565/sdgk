package com.example.admission.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预测结果实体，对应 prediction_result 表.
 */
@Data
@TableName("prediction_result")
public class PredictionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("profile_hash")
    private String profileHash;

    @TableField("plan_id")
    private Long planId;

    @TableField("plan_data_version")
    private Long planDataVersion;

    @TableField("model_version")
    private String modelVersion;

    @TableField("probability")
    private Integer probability;

    @TableField("predicted_rank_min")
    private Integer predictedRankMin;

    @TableField("predicted_rank_max")
    private Integer predictedRankMax;

    @TableField("label")
    private String label;

    @TableField("reason_text")
    private String reasonText;

    @TableField("reason_code")
    private String reasonCode;

    @TableField("is_valid")
    private Integer isValid;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // --- Label constants ---
    public static final String LABEL_CHONG = "冲";
    public static final String LABEL_WEN = "稳";
    public static final String LABEL_BAO = "保";
}
