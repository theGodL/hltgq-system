package com.qgyun.hltgq.hltgqsystem.loginlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录请求（来自中间件 nginx access.log 的登录请求：真实 IP / UA / 时间）。
 * <p>用于与登录事件（操作人/终端/会话，来自平台认证日志）按时间就近合并。
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_login_request\"")
public class LoginRequest {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("\"ip\"")
    private String ip;

    @TableField("\"user_agent\"")
    private String userAgent;

    @TableField("\"login_at\"")
    private LocalDateTime loginAt;

    /** 是否已合并（0 未 / 1 已） */
    @TableField("\"matched\"")
    private String matched;

    @TableField("\"created_at\"")
    private LocalDateTime createdAt;
}
