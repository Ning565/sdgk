package com.example.admission.analytics.controller;

import com.example.admission.analytics.dto.AnalyticsEventRequest;
import com.example.admission.analytics.service.AnalyticsService;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.TraceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点事件接收控制器.
 *
 * <p>基础路径： {@code /api/v1/analytics/events}</p>
 * <p>接收前端埋点事件，MVP 阶段仅记录日志。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics/events")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 接收埋点事件.
     *
     * @param req 请求对象uest HTTP 请求 埋点事件请求
     * @return 成功响应（含 traceId）
     */
    @PostMapping
    public ApiResponse<Void> receiveEvent(@Valid @RequestBody AnalyticsEventRequest request) {
        log.debug("Analytics event received: event={}, userId={}, traceId={}",
                request.getEvent(), request.getUserId(), TraceContext.getTraceId());

        analyticsService.saveEvent(request);

        return ApiResponse.success();
    }
}
