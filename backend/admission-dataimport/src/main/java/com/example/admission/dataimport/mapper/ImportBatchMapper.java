package com.example.admission.dataimport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.dataimport.entity.ImportBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入批次 Mapper 接口.
 *
 * @author admission-system
 */
@Mapper
public interface ImportBatchMapper extends BaseMapper<ImportBatch> {
}
