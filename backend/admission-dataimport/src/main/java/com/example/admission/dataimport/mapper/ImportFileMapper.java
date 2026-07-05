package com.example.admission.dataimport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.dataimport.entity.ImportFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入文件 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface ImportFileMapper extends BaseMapper<ImportFile> {
}
