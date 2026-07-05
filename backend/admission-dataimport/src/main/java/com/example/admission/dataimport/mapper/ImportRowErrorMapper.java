package com.example.admission.dataimport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.dataimport.entity.ImportRowError;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入行级错误 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface ImportRowErrorMapper extends BaseMapper<ImportRowError> {
}
