package com.agri_system.infrastructure.common.response;

import com.agri_system.infrastructure.common.error.SystemErrorCode;
import org.slf4j.MDC;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 统一响应体
 */
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;
    private final String traceId;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
        this.traceId = MDC.get("traceId");
    }

    public static <T> R<T> success(T data) {
        return new R<>(SystemErrorCode.SUCCESS.getCode(), SystemErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> success() {
        return success(null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(SystemErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
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

    public long getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }
}






