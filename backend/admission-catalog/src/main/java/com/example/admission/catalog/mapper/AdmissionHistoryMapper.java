package com.example.admission.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.catalog.entity.AdmissionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 历史录取数据 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface AdmissionHistoryMapper extends BaseMapper<AdmissionHistory> {

    /**
     * 根据 plan_series_id 查询历史录取数据（按年份降序排列）.
     *
     * @param planSeriesId 招生计划系列ID
     * @return 历史录取数据列表
     */
    @Select("SELECT * FROM admission_history WHERE plan_series_id = #{planSeriesId} AND deleted = 0 ORDER BY year DESC")
    List<AdmissionHistory> selectByPlanSeriesId(@Param("planSeriesId") Long planSeriesId);

    /**
     * 根据 plan_series_id 查询近N年历史录取数据.
     *
     * @param planSeriesId 招生计划系列ID
     * @param limitYears   最近N年
     * @return 历史录取数据列表
     */
    @Select("SELECT * FROM admission_history WHERE plan_series_id = #{planSeriesId} AND deleted = 0 ORDER BY year DESC LIMIT #{limitYears}")
    List<AdmissionHistory> selectByPlanSeriesIdWithLimit(@Param("planSeriesId") Long planSeriesId, @Param("limitYears") int limitYears);
}
