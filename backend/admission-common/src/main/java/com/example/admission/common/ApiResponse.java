package com.example.admission.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 统一 API 响应包装器，包含 traceId、code、message 和 data。
 *
 * @param <T> 数据负载的类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String traceId;
    private int code;
    private String message;
    private T data;
    private Instant timestamp;

    private ApiResponse() {
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = ErrorCode.SUCCESS.getCode();
        response.message = ErrorCode.SUCCESS.getMessage();
        response.data = data;
        response.traceId = TraceContext.getTraceId();
        return response;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = errorCode.getCode();
        response.message = errorCode.getMessage();
        response.traceId = TraceContext.getTraceId();
        return response;
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = errorCode.getCode();
        response.message = message;
        response.traceId = TraceContext.getTraceId();
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.traceId = TraceContext.getTraceId();
        return response;
    }

    // --- Getter ---

    public String getTraceId() {
        return traceId;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
