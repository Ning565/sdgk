package com.example.admission.analytics.service;

import com.example.admission.analytics.dto.AnalyticsEventRequest;
import com.example.admission.common.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 埋点事件服务（MVP：仅日志记录）.
 *
 * <p>MVP 阶段将事件序列化为 JSON 并输出到日志，附带 traceId 用于追踪。
 * 后续可扩展为写入 Kafka / ClickHouse 等分析管道。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ObjectMapper objectMapper;

    /**
     * 保存埋点事件（MVP：记录到日志）.
     *
     * @param req 请求对象uest HTTP 请求 埋点事件请求
     */
    public void saveEvent(AnalyticsEventRequest request) {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("traceId", TraceContext.getTraceId());
        eventMap.put("timestamp", Instant.now().toString());
        eventMap.put("event", request.getEvent());
        eventMap.put("userId", request.getUserId());
        eventMap.put("year", request.getYear());
        eventMap.put("planId", request.getPlanId());
        eventMap.put("formId", request.getFormId());
        eventMap.put("properties", request.getProperties());

        try {
            String json = objectMapper.writeValueAsString(eventMap);
            log.info("ANALYTICS_EVENT: {}", json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize analytics event: {}", e.getMessage());
        }
    }
}
