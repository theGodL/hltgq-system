package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

/**
 * 管理端整树保存的单个节点提交项。
 * <p>数组顺序即展示顺序：同父节点按出现在 nodes 中的先后排列。
 */
@Data
public class SaveNode {

    /** 平台菜单 ID */
    private String menuId;

    /** 目标父：'#ROOT#'=顶层 / 其他菜单 id */
    private String parentId;

    /** 当前显示名（与平台原名一致时后端自动不落配置） */
    private String displayName;

    /** 目标显隐 */
    private Boolean visible;
}
