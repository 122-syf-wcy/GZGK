package com.gzly.common;

import lombok.Data;

/**
 * 统一响应格式 — 与前端 types/index.ts 中 Result<T> 对齐
 */
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.code = -1;
        r.message = message;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
