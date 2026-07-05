package com.example.admission.candidate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.candidate.entity.CandidateProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 考生档案 Mapper 接口.
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供基础的 CRUD 操作，
 * 同时定义自定义查询方法。</p>
 */
@Mapper
public interface CandidateProfileMapper extends BaseMapper<CandidateProfile> {

    /**
     * 根据用户ID和年份查询考生档案.
     *
     * <p>同一用户同一年度最多只有一份档案。</p>
     *
     * @param userId 用户ID
     * @param year 年份   高考年份
     * @return CandidateProfile 或 null
     */
    @Select("SELECT * FROM candidate_profile WHERE user_id = #{userId} AND year = #{year}")
    CandidateProfile selectByUserIdAndYear(@Param("userId") Long userId, @Param("year") Integer year);
}
