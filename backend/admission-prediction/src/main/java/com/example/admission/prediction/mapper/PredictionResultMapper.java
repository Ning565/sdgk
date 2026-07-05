package com.example.admission.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.prediction.entity.PredictionResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus Mapper 接口 —— {@link PredictionResult}.
 */
@Mapper
public interface PredictionResultMapper extends BaseMapper<PredictionResult> {

    /**
     * 根据 profile_hash、plan_id、data_version 和 model_version 查找缓存的预测结果。
     */
    @Select("SELECT * FROM prediction_result WHERE profile_hash = #{profileHash} AND plan_id = #{planId} AND plan_data_version = #{planDataVersion} AND model_version = #{modelVersion} AND is_valid = 1 LIMIT 1")
    PredictionResult findCached(@Param("profileHash") String profileHash,
                                @Param("planId") Long planId,
                                @Param("planDataVersion") Long planDataVersion,
                                @Param("modelVersion") String modelVersion);
}
