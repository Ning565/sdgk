package com.example.admission.volunteer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.volunteer.entity.VolunteerItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 志愿项 Mapper.
 */
@Mapper
public interface VolunteerItemMapper extends BaseMapper<VolunteerItem> {

    /**
     * 将 formId 下 sortOrder >= fromSort 的记录向后移动 offset 位.
     * 用于重整排序时使用，避免主键冲突.
     */
    @Update("UPDATE volunteer_item SET sort_order = sort_order + #{offset} " +
            "WHERE form_id = #{formId} AND sort_order >= #{fromSort}")
    int shiftSortOrder(@Param("formId") Long formId,
                       @Param("fromSort") int fromSort,
                       @Param("offset") int offset);

    /**
     * 查询志愿表中最大的 sortOrder.
     */
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(MAX(sort_order), 0) FROM volunteer_item WHERE form_id = #{formId}")
    int maxSortOrder(@Param("formId") Long formId);
}
