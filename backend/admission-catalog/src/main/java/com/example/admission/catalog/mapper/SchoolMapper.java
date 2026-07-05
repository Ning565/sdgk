package com.example.admission.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.catalog.entity.School;
import org.apache.ibatis.annotations.Mapper;

/**
 * 院校 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface SchoolMapper extends BaseMapper<School> {
}
