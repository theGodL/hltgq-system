package com.qgyun.hltgq.hltgqsystem.oplog.vo;

import lombok.Data;

/**
 * 操作日志查询条件。
 */
@Data
public class OpLogQueryRequest {

    /** 菜单分组 */
    private String groupName;

    /** 物理表名 */
    private String tableName;

    /** 操作类型 INSERT / UPDATE / DELETE */
    private String opType;

    /** 操作人 ID */
    private String operatorId;

    /** 关键字（业务 ID / 操作人姓名 / 表单名 模糊匹配） */
    private String keyword;

    /** 开始时间 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 结束时间 yyyy-MM-dd HH:mm:ss */
    private String endTime;

    /** 页码（从 1 起） */
    private Long pageNo = 1L;

    /** 每页条数 */
    private Long pageSize = 20L;
}
