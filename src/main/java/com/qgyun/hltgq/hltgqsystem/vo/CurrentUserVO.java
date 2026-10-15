package com.qgyun.hltgq.hltgqsystem.vo;

import lombok.Data;

/**
 * 当前登录人返回体（仅业务必需字段）。
 */
@Data
public class CurrentUserVO {

    /** 用户主键 */
    private String userId;

    /** 企业编码 */
    private String corpCode;

    /** 是否超级管理员 */
    private String superAdmin;

    /** 是否管理员（平台超管 或 命中管理员角色），供前端开放入口，与 @RequireAdmin 校验口径一致 */
    private Boolean admin;

    /** 登录名（t_apaas_uc_user.login_name） */
    private String loginName;
}
