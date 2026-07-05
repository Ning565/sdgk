package com.example.admission.common;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * 基于 SLF4J MDC 的线程本地 Trace 上下文。
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
