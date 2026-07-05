package com.example.admission.candidate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.candidate.entity.ScoreRankSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 一分一段表 Mapper 接口.
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供对 score_rank_segment 表的 CRUD 操作。
 * 查询时通过 data_version_id 限定当前生效的版本，确保数据准确性。</p>
 */
@Mapper
public interface ScoreRankSegmentMapper extends BaseMapper<ScoreRankSegment> {

    /**
     * 根据年份、分数和生效版本ID精确匹配一分一段表记录.
     *
     * <p>通过 data_version_id 限定当前生效的数据版本，
     * 确保查询结果来自已发布的正式数据。</p>
     *
     * @param year 年份          高考年份
     * @param score         高考分数（精确匹配）
     * @param dataVersionId 当前生效的数据版本ID
     * @return ScoreRankSegment 或 null
     */
    @Select("SELECT * FROM score_rank_segment WHERE year = #{year} AND score = #{score} AND data_version_id = #{dataVersionId}")
    ScoreRankSegment selectByYearAndScore(@Param("year") Integer year,
                                           @Param("score") Integer score,
                                           @Param("dataVersionId") Long dataVersionId);
}
