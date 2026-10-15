package com.qgyun.hltgq.hltgqsystem.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsystem.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsystem.loginlog.entity.LoginLog;
import com.qgyun.hltgq.hltgqsystem.loginlog.service.LoginLogService;
import com.qgyun.hltgq.hltgqsystem.loginlog.vo.LoginLogQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录日志接口。
 * <p>查询/采集：仅系统管理员；上报（/login-log/report）：白名单放行，可带 token 校验。
 */
@RestController
@RequestMapping("/login-log")
public class LoginLogController {

    @Autowired
    private LoginLogService loginLogService;

    @Value("${loginlog.report.token:}")
    private String reportToken;

    /** 分页查询登录日志 */
    @GetMapping("/list")
    @RequireAdmin
    public IPage<LoginLog> list(LoginLogQueryRequest request) {
        return loginLogService.page(request);
    }

    /** 手动触发：会话采集 + 登录请求合并 + 终端兜底 */
    @PostMapping("/collect")
    @RequireAdmin
    public Map<String, Object> collect() {
        int n = loginLogService.collectFromRedis();
        int m = loginLogService.matchPending();
        int t = loginLogService.fixTerminalFromUa();
        int g = loginLogService.refillRegion();
        Map<String, Object> r = new HashMap<>();
        r.put("inserted", n);
        r.put("matched", m);
        r.put("terminalFixed", t);
        r.put("regionFixed", g);
        return r;
    }

    /**
     * 登录事件上报（供登录入口/网关调用）。
     * <p>body 字段：sessionId, operator, loginType, terminal, ip, userAgent, region, loginAt(yyyy-MM-dd HH:mm:ss)。
     */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody Map<String, String> body,
                                      @RequestHeader(value = "X-Login-Token", required = false) String token) {
        Map<String, Object> r = new HashMap<>();
        if (!tokenOk(token)) {
            r.put("success", false);
            r.put("message", "令牌校验失败");
            return r;
        }
        loginLogService.report(body.get("sessionId"), body.get("operator"), body.get("loginType"),
                body.get("terminal"), body.get("ip"), body.get("userAgent"), body.get("region"),
                parseTime(body.get("loginAt")));
        r.put("success", true);
        return r;
    }

    /**
     * 登录请求上报（中间件 nginx 采集）：body 字段 ip / userAgent / loginAt。
     */
    @PostMapping("/report-request")
    public Map<String, Object> reportRequest(@RequestBody Map<String, String> body,
                                             @RequestHeader(value = "X-Login-Token", required = false) String token) {
        Map<String, Object> r = new HashMap<>();
        if (!tokenOk(token)) {
            r.put("success", false);
            r.put("message", "令牌校验失败");
            return r;
        }
        loginLogService.reportRequest(body.get("ip"), body.get("userAgent"), parseTime(body.get("loginAt")));
        r.put("success", true);
        return r;
    }

    private boolean tokenOk(String token) {
        return !StringUtils.hasText(reportToken) || reportToken.equals(token);
    }

    private static LocalDateTime parseTime(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim().replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }
}
