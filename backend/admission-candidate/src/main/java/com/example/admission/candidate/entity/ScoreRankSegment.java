package com.example.admission.candidate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 一分一段表实体，对应 score_rank_segment 表.
 *
 * <p>存储每年高考各分数段的累计人数和分段人数。
 * 例如: 2025年600分，累计人数15000人，分段人数500人。
 * 累计人数即为考生在该分数的省排名位次。</p>
 */
@Data
@TableName("score_rank_segment")
public class ScoreRankSegment {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属数据版本ID，关联 data_version 表 */
    @TableField("data_version_id")
    private Long dataVersionId;

    /** 高考年份 */
    private Integer year;

    /** 高考分数 */
    private Integer score;

    /** 累计人数（即该分数及以上的总人数，等于省排名位次） */
    private Integer cumulativeCount;

    /** 分段人数（该分数段的考生人数） */
    private Integer segmentCount;
}
