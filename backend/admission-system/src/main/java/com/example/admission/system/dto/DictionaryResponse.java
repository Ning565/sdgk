package com.example.admission.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 字典项响应 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryResponse {

    /** 字典类型，如 education_level, school_type */
    private String type;

    /** 字典项代码 */
    private String code;

    /** 字典项名称 */
    private String name;

    /** 排序 */
    private Integer sortOrder;

    /**
     * 嵌套子项（可选）.
     */
    private List<DictionaryResponse> children;
}
