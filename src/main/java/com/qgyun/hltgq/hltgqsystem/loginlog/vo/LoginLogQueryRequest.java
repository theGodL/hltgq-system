package com.qgyun.hltgq.hltgqsystem.loginlog.vo;

import lombok.Data;

/**
 * 登录日志查询条件。
 */
@Data
public class LoginLogQueryRequest {

    /** 关键字（operator_name / ip / session_id / user_agent 模糊） */
    private String keyword;

    /** 登录方式 */
    private String loginType;

    /** 登录终端 */
    private String terminal;

    /** 操作人 ID */
    private String operator;

    /** 开始时间 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 结束时间 yyyy-MM-dd HH:mm:ss */
    private String endTime;

    private Long pageNo = 1L;
    private Long pageSize = 20L;
}
