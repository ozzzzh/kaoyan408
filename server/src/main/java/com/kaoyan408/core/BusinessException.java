package com.kaoyan408.core;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 翻译为统一响应体。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}