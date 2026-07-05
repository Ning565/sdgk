package com.example.admission.export.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出记录实体，对应 export_record 表.
 */
@Data
@TableName("export_record")
public class ExportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 志愿表ID */
    private Long formId;

    /** 文件名 */
    private String fileName;

    /** 文件存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 导出时是否包含错误 */
    private Boolean confirmedWithErrors;

    /** 导出时的数据版本ID */
    private Long dataVersionId;

    /** traceId */
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
