package com.kaoyan408.core;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常 → 统一响应体。HTTP 状态一律 200，语义放在 body.code。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException ex) {
        return ResponseEntity.ok(Result.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 未匹配到任何端点。文案与 408os 保持一致，便于前端识别。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.ok(Result.error(404, notFoundMessage(request)));
    }

    /** throw-exception-if-no-handler-found=true 时，未匹配端点会走到这里。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> noHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.ok(Result.error(404, notFoundMessage(request)));
    }

    private String notFoundMessage(HttpServletRequest request) {
        return String.format("No endpoint %s %s.", request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalidBody(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder("参数校验失败: ");
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> sb.append(fe.getField()).append(' ').append(fe.getDefaultMessage()).append("; "));
        return ResponseEntity.ok(Result.error(400, sb.toString().trim()));
    }

    /**
     * 请求体不是合法 JSON（例如客户端漏转义引号）。
     *
     * <p>不单独捕获的话会落到下面的兜底，表现为「400 的错被当成 500 上报」，
     * 日志里还多一条 ERROR 噪音，排查时容易误判成服务端故障。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> unreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.ok(Result.error(400, "请求体格式错误，应为合法 JSON。"));
    }

    /** 缺少必填查询参数或参数类型不对（如 year=abc），同样属于客户端错误。 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> badParameter(Exception ex) {
        return ResponseEntity.ok(Result.error(400, "请求参数不合法: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unhandled(Exception ex) {
        log.error("未处理异常: {}", ex.getMessage(), ex);
        return ResponseEntity.ok(Result.error(500, "系统异常，请稍后重试。"));
    }
}