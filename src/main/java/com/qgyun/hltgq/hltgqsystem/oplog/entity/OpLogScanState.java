package com.qgyun.hltgq.hltgqsystem.oplog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志扫描水位线（自建表 t_auto_hltgq_op_log_state）：每张被监控表一行。
 * <p>watermark 存 "yyyy-MM-dd HH:mm:ss.SSS" 字符串（与 to_char 输出一致，避免 Kingbase 时间类型歧义），
 * 下次扫描从此水位线之后取增量。
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_op_log_state\"")
public class OpLogScanState {

    /** 物理表名（主键） */
    @TableId(value = "\"table_name\"", type = IdType.INPUT)
    private String tableName;

    /** 已扫描到的操作时间水位线（yyyy-MM-dd HH:mm:ss.SSS） */
    @TableField("\"watermark\"")
    private String watermark;

    /** 最近一次扫描时间 */
    @TableField("\"last_scan_at\"")
    private LocalDateTime lastScanAt;
}
