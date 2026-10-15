package com.qgyun.hltgq.hltgqsystem.oplog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志（自建表 t_auto_hltgq_op_log）。
 * <p>由 {@code OpLogScanService} 按监控清单扫描业务/平台表的审计列（created_at/updated_at）
 * 与快照 diff 生成：仅记录人工操作（过滤 SYSTEM 等机器操作人）。
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_op_log\"")
public class OpLog {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    /** 菜单分组（如 应急管理 / 运行监测） */
    @TableField("\"group_name\"")
    private String groupName;

    /** 物理表名 */
    @TableField("\"table_name\"")
    private String tableName;

    /** 表单中文名 */
    @TableField("\"form_name\"")
    private String formName;

    /** 业务记录 ID */
    @TableField("\"biz_id\"")
    private String bizId;

    /** 操作类型：INSERT / UPDATE / DELETE */
    @TableField("\"op_type\"")
    private String opType;

    /** 操作时间（取自表内审计字段；删除以捕获时刻为准） */
    @TableField("\"op_at\"")
    private LocalDateTime opAt;

    /** 操作人 ID（t_apaas_uc_user.id 短 ID） */
    @TableField("\"operator_id\"")
    private String operatorId;

    /** 操作人姓名（落库冗余，防改名/离职后无法追溯） */
    @TableField("\"operator_name\"")
    private String operatorName;

    /** 变更字段摘要（UPDATE 时为发生变化的列名，逗号分隔） */
    @TableField("\"change_fields\"")
    private String changeFields;

    /** 变更前快照（INSERT 为空；DELETE 仅有 before） */
    @TableField("\"before_json\"")
    private String beforeJson;

    /** 变更后快照（DELETE 为空） */
    @TableField("\"after_json\"")
    private String afterJson;

    /** 日志记录时间 */
    @TableField("\"created_at\"")
    private LocalDateTime createdAt;
}
