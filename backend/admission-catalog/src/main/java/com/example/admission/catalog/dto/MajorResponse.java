package com.example.admission.catalog.dto;

import lombok.Data;

/**
 * 标准专业响应.
 *
 * @author admission-system
 */
@Data
public class MajorResponse {

    /** 专业ID */
    private Long id;

    /** 专业代码 */
    private String code;

    /** 专业名称 */
    private String name;

    /** 专业门类代码 */
    private String categoryCode;

    /** 专业门类名称 */
    private String categoryName;

    /** 专业类代码 */
    private String subcategoryCode;

    /** 专业类名称 */
    private String subcategoryName;

    /** 学历层次 */
    private String educationLevel;
}
