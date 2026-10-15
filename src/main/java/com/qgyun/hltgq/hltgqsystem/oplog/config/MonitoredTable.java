package com.qgyun.hltgq.hltgqsystem.oplog.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 被监控表配置（来自 oplog-tables.json）。
 * <p>所有表共用平台标准审计列：id / corp_code / created_at / created_by / updated_at / updated_by。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoredTable {

    /** 物理表名（不含 schema，统一为 qixiao-apaas） */
    private String tableName;

    /** 表单中文名（展示用） */
    private String formName;

    /** 所属菜单分组（运行时由所属 group 注入） */
    private String groupName;

    /** 所属应用名（运行时由所属 group 注入） */
    private String appName;

    /** 是否跟踪删除（快照对比识别"上次存在本次消失"；大表关闭以省开销） */
    private boolean trackDelete;

    /** 租户过滤（平台表按 corp_code='hltgq' 限定；业务表为空不过滤） */
    private String corpFilter;

    /** 软删除列名（如 deleted / del_flag）；该列翻转为删除值即记为删除操作 */
    private String softDeleteColumn;

    /** 软删除判定值（命中任一即视为已删除，如 1/true/#1#） */
    private List<String> softDeleteValues;

    /** 敏感字段（落库时值以 *** 脱敏，仅保留"发生变更"事件） */
    private List<String> maskFields;
}
