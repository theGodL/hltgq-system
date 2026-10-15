package com.qgyun.hltgq.hltgqsystem.oplog.task;

import com.qgyun.hltgq.hltgqsystem.oplog.service.OpLogScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志扫描定时任务。
 * <p>启动后延迟执行一次（首次回溯 initialLookbackDays 天，便于回补历史人工操作），随后按 cron 周期增量扫描。
 */
@Component
public class OpLogScanTask {

    private static final Logger log = LoggerFactory.getLogger(OpLogScanTask.class);

    @Autowired
    private OpLogScanService scanService;

    /** 启动后一次性初始扫描（默认 30s；fixedDelay 极大值实现"只跑一次"） */
    @Scheduled(initialDelayString = "${oplog.scan.initial-delay-ms:30000}", fixedDelay = Long.MAX_VALUE)
    public void initialScan() {
        log.info("操作日志初始扫描开始");
        scanService.scanAll();
    }

    /** 周期增量扫描 */
    @Scheduled(cron = "${oplog.scan.cron:0 0/3 * * * ?}")
    public void scheduledScan() {
        scanService.scanAll();
    }
}
