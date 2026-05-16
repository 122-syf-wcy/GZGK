package com.gzly.common.exception;

import com.gzly.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        HttpStatus status = switch (e.getCode()) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 410 -> HttpStatus.GONE;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数绑定失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst().orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingRequestParameter(MissingServletRequestParameterException e) {
        return Result.fail(400, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(400, "参数格式错误：" + e.getName());
    }

    /**
     * 请求体不可读 / JSON 解析失败 — 典型于客户端误传 form 而 controller 要 JSON，
     * 或者把空请求体提交到 @RequestBody。属于客户端错误，不需要打 ERROR。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("请求体不可读: {}", e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage());
        return Result.fail(400, "请求体格式错误");
    }

    /**
     * HTTP Method 不支持（例如对 GET 接口发 POST），典型扫描器行为，warn 即可。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 方法不支持: method={} supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(405, "请求方法不支持");
    }

    /**
     * Content-Type 不支持（415），通常是客户端没设 application/json。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Content-Type 不支持: contentType={} supported={}", e.getContentType(), e.getSupportedMediaTypes());
        return Result.fail(415, "请求 Content-Type 不支持");
    }

    /**
     * Spring Boot 3 的 NoResourceFoundException — 静态资源 / 未知路由 404。
     * 爬虫扫描会刷大量 404，必须降级到 debug 不打 stacktrace，否则日志被淹没。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("资源未找到: {}", e.getResourcePath());
        return Result.fail(404, "资源未找到");
    }

    /**
     * 上传文件过大（413）。文件上传入口已经在 application.yml 限定 10MB / 20MB。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: maxSize={}", e.getMaxUploadSize());
        return Result.fail(413, "上传文件过大");
    }

    /**
     * 数据库相关异常单独打 ERROR + 503，区分于业务异常的 OK；
     * 让运维能直接 grep "DB异常" 拉报警，不和业务异常混淆。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<?> handleDataAccess(DataAccessException e) {
        log.error("DB异常", e);
        return Result.fail(503, "数据访问暂不可用，请稍后再试");
    }

    /**
     * 客户端已断开（典型于 SSE / 长连接被前端关闭、移动浏览器切后台杀进程）。
     * 此时再尝试写响应必然失败，且与服务端无关，不应刷 ERROR 干扰排障。
     * 兼容 Tomcat 的 ClientAbortException 和 Spring 的 AsyncRequestNotUsableException —
     * 都是 IOException 子类，按类名匹配以避免硬编译依赖 catalina/web-async。
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Result<?>> handleIO(IOException e) {
        String type = e.getClass().getSimpleName();
        if ("ClientAbortException".equals(type)
                || "AsyncRequestNotUsableException".equals(type)
                || "AsyncRequestTimeoutException".equals(type)) {
            log.debug("客户端断开: {} {}", type, e.getMessage());
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        log.error("IO异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务暂不可用"));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "服务器内部错误");
    }
}
