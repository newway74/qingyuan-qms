package com.qms.common.result;

import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一返回体
 */
@Data
public class R<T> implements Serializable {

    public static final String SUCCESS_CODE = "0";

    private String code;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok() {
        return build(SUCCESS_CODE, "ok", null);
    }

    public static <T> R<T> ok(T data) {
        return build(SUCCESS_CODE, "ok", data);
    }

    public static <T> R<T> fail(String code, String message) {
        return build(code, message, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return build(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return build(resultCode.getCode(), message, null);
    }

    private static <T> R<T> build(String code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }
}
