package com.qgyun.hltgq.hltgqsystem.controller;

import com.qgyun.hltgq.hltgqsystem.auth.SessionUnavailableException;
import com.qgyun.hltgq.hltgqsystem.auth.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：统一参数错误与鉴权错误的 HTTP 语义。
 * <p>IllegalArgumentException → 400；UnauthorizedException → 401；SessionUnavailableException → 503。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", e.getMessage() == null ? "请求参数错误" : e.getMessage());
        return result;
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUnauthorized(UnauthorizedException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", e.getMessage() == null ? "未登录" : e.getMessage());
        return result;
    }

    @ExceptionHandler(SessionUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleSessionUnavailable(SessionUnavailableException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 503);
        result.put("message", e.getMessage() == null ? "会话服务不可用" : e.getMessage());
        return result;
    }
}
