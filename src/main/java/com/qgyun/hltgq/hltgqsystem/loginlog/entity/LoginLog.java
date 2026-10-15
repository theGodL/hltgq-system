package com.qgyun.hltgq.hltgqsystem.loginlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（自建表 t_auto_hltgq_login_log）。
 * <p>采集来源：Redis 会话扫描（真实登录）+ 请求侧回填 IP/UA/终端 + 上报接口。
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_login_log\"")
public class LoginLog {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    /** 操作人（t_apaas_uc_user.id 短 ID） */
    @TableField("\"operator\"")
    private String operator;

    /** 操作人姓名 */
    @TableField("\"operator_name\"")
    private String operatorName;

    /** 登录方式：CORP 账户密码 / MOBILE_VERIFY 手机验证码 / DDING 钉钉 / WX 企业微信 */
    @TableField("\"login_type\"")
    private String loginType;

    /** 登录终端 */
    @TableField("\"terminal\"")
    private String terminal;

    /** 登录 IP */
    @TableField("\"ip\"")
    private String ip;

    /** 会话标识（sessionId） */
    @TableField("\"session_id\"")
    private String sessionId;

    /** 浏览器 agent */
    @TableField("\"user_agent\"")
    private String userAgent;

    /** 登录地点 */
    @TableField("\"region\"")
    private String region;

    /** 操作时间（登录时间） */
    @TableField("\"login_at\"")
    private LocalDateTime loginAt;

    /** 记录创建时间 */
    @TableField("\"created_at\"")
    private LocalDateTime createdAt;
}
