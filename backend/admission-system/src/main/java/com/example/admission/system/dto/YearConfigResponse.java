package com.example.admission.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 年度配置响应 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearConfigResponse {

    /** 年度 */
    private Integer year;

    /** 最低分 */
    private Integer scoreMin;

    /** 最高分 */
    private Integer scoreMax;

    /** 志愿数量上限 */
    private Integer volunteerLimit;

    /** 是否开放（可用） */
    private Boolean isOpen;

    /** 备注 */
    private String remark;
}
