package com.example.admission.catalog.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 院校详情响应，继承 SchoolResponse，增加招生计划统计信息.
 *
 * @author admission-system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SchoolDetailResponse extends SchoolResponse {

    /** 当年在山东招生专业数 (从当前生效版本查询) */
    private Integer planCount;

    /** 符合考生条件的专业数 (需要考生上下文，无上下文时返回 null) */
    private Integer eligiblePlanCount;
}
