package com.example.admission.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 埋点事件请求 DTO.
 */
@Data
public class AnalyticsEventRequest {

    /** 事件名称，如 page_view, plan_click, form_submit */
    @NotBlank(message = "事件名称不能为空")
    private String event;

    /** 用户ID（可选） */
    private Long userId;

    /** 招生年度（可选） */
    private Integer year;

    /** 关联计划ID（可选） */
    private Long planId;

    /** 关联志愿表ID（可选） */
    private Long formId;

    /** 扩展属性（可选） */
    private Map<String, Object> properties;
}
