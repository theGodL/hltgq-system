package com.qgyun.hltgq.hltgqsystem.loginlog.task;

import com.qgyun.hltgq.hltgqsystem.loginlog.service.LoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 登录日志采集定时任务：周期扫描 Redis 会话，采集真实登录。
 */
@Component
public class LoginLogCollectTask {

    @Autowired
    private LoginLogService loginLogService;

    @Value("${loginlog.collect.enabled:true}")
    private boolean enabled;

    /** 启动延迟 + 周期（默认 20s 后首次，之后每 2 分钟） */
    @Scheduled(initialDelayString = "${loginlog.collect.initial-delay-ms:20000}",
            fixedDelayString = "${loginlog.collect.delay-ms:120000}")
    public void collect() {
        if (enabled) {
            loginLogService.collectFromRedis();
        }
        // 登录请求与登录事件按时间合并
        try {
            loginLogService.matchPending();
            loginLogService.fixTerminalFromUa();
            loginLogService.refillRegion();
        } catch (Exception ignore) {
            // 合并失败不影响采集
        }
    }
}
