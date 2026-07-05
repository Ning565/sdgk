package com.example.admission.dataimport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 当前生效数据版本 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface ActiveDataVersionMapper extends BaseMapper<ActiveDataVersion> {

    /**
     * 查询当前激活的版本ID（读操作，不加锁）.
     *
     * @param dataType 数据类型
     * @param year 年份     年份
     * @return dataVersionId 或 null
     */
    @Select("SELECT data_version_id FROM active_data_version WHERE data_type = #{dataType} AND year = #{year}")
    Long getActiveVersionId(@Param("dataType") String dataType, @Param("year") Integer year);

    /**
     * 使用行锁查询当前激活的版本记录（用于发布时的原子切换）.
     *
     * @param dataType 数据类型
     * @param year 年份     年份
     * @return ActiveDataVersion 或 null
     */
    @Select("SELECT * FROM active_data_version WHERE data_type = #{dataType} AND year = #{year} FOR UPDATE")
    ActiveDataVersion selectForUpdate(@Param("dataType") String dataType, @Param("year") Integer year);
}
