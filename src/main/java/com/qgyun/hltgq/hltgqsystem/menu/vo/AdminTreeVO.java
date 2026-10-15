package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理端菜单树响应体。
 */
@Data
public class AdminTreeVO {

    /** 配置版本（配置表最大 updated_at，yyyyMMddHHmmss；无配置为空串） */
    private String version;

    /** 锁定节点 ID（页面禁改） */
    private List<String> lockedMenuIds;

    /** 生效结构树（顶层列表） */
    private List<AdminTreeNodeVO> tree;
}
