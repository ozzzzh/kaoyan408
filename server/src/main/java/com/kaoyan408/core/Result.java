package com.kaoyan408.core;

/**
 * 统一响应体。所有接口（含异常、鉴权失败）都返回 HTTP 200 + 该结构，
 * 业务语义由 code 表达（0 = 成功）。
 */
public final class Result<T> {

    private final int code;
    private final String msg;
    private final T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, null, data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, null, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}