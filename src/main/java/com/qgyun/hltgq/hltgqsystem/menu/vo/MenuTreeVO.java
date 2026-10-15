package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

import java.util.List;

/**
 * 展示端菜单树数据体（响应 data 部分）。
 */
@Data
public class MenuTreeVO {

    /** 配置版本（配置表最大 updated_at，yyyyMMddHHmmss；无配置为空串） */
    private String version;

    /** 菜单树（数组顺序即展示顺序；灌区一张图恒为第一位） */
    private List<RenderNodeVO> menus;
}
