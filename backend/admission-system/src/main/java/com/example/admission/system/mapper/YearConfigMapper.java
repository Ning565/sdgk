package com.example.admission.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.system.entity.YearConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper 接口 —— {@link YearConfig}.
 */
@Mapper
public interface YearConfigMapper extends BaseMapper<YearConfig> {

    /**
     * 查询所有开放的年度配置，按年度降序排列.
     */
    @Select("SELECT * FROM year_config WHERE is_open = 1 ORDER BY year DESC")
    List<YearConfig> selectActiveYears();

    /**
     * 根据年度查询配置.
     */
    @Select("SELECT * FROM year_config WHERE year = #{year} LIMIT 1")
    YearConfig selectByYear(@Param("year") Integer year);
}
