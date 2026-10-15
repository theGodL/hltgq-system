package com.qgyun.hltgq.hltgqsystem.oplog.vo;

import lombok.Data;

/**
 * 操作日志扫描结果。
 */
@Data
public class ScanResultVO {

    /** 是否执行（扫描开关关闭/并发中为 false） */
    private boolean executed;

    /** 扫描表数量 */
    private int tables;

    /** 新增日志数 */
    private int inserts;

    /** 修改日志数 */
    private int updates;

    /** 删除日志数 */
    private int deletes;

    /** 提示信息 */
    private String message;

    public int total() {
        return inserts + updates + deletes;
    }
}
