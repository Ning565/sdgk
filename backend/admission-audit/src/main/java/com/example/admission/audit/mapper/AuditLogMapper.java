package com.example.admission.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper 接口 —— {@link AuditLog}.
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
