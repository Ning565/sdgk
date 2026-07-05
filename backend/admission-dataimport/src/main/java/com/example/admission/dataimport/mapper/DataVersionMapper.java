package com.example.admission.dataimport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.dataimport.entity.DataVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据版本 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface DataVersionMapper extends BaseMapper<DataVersion> {

    /**
     * 查询指定数据类型和年份的最大版本号.
     *
     * @param dataType 数据类型
     * @param year 年份     年份
     * @return 最大版本号，若无则返回 0
     */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM data_version WHERE data_type = #{dataType} AND year = #{year}")
    int selectMaxVersionNo(@Param("dataType") String dataType, @Param("year") Integer year);

    /**
     * 查询指定数据类型和年份当前激活的版本.
     *
     * @param dataType 数据类型
     * @param year 年份     年份
     * @return DataVersion 或 null
     */
    @Select("SELECT dv.* FROM data_version dv " +
            "INNER JOIN active_data_version adv ON dv.id = adv.data_version_id " +
            "WHERE adv.data_type = #{dataType} AND adv.year = #{year}")
    DataVersion selectActiveVersion(@Param("dataType") String dataType, @Param("year") Integer year);
}
