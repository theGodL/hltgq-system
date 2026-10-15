package com.qgyun.hltgq.hltgqsystem.oplog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志行快照（自建表 t_auto_hltgq_op_log_snapshot）：记录每张监控表当前已知行的 JSON 快照。
 * <p>用途：UPDATE 时提供 before 快照；DELETE 时通过"上次存在、本次消失"识别并保留 before。
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_op_log_snapshot\"")
public class OpLogSnapshot {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    /** 物理表名 */
    @TableField("\"table_name\"")
    private String tableName;

    /** 业务记录 ID */
    @TableField("\"biz_id\"")
    private String bizId;

    /** 行快照（row_to_json 结果） */
    @TableField("\"row_json\"")
    private String rowJson;

    /** 最近一次见到该行的时间 */
    @TableField("\"seen_at\"")
    private LocalDateTime seenAt;
}
