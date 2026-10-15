package com.qgyun.hltgq.hltgqsystem.menu.entity;

import lombok.Data;

/**
 * 平台菜单只读对象（t_apaas_sys_menu，corp_code='hltgq'）。
 * <p>本模块只读平台菜单库：作为管理端的"全量结构 + 元数据"来源，
 * 以及展示端的排序基准（show_order）与类型来源（type）。
 * 不写入、不修改该表。
 */
@Data
public class PlatformMenu {

    /** 平台菜单 ID */
    private String id;

    /** 菜单名称（平台原名） */
    private String name;

    /** 类型：#GROUP#=分组，#LINK#=叶子菜单 */
    private String type;

    /** 分类：#APP# / #SYSTEM# / #DESIGN# 等 */
    private String category;

    /** 上级菜单 ID（顶层为 NULL） */
    private String appParent;

    /** 平台排序号（可为小数） */
    private Double showOrder;

    /** 企业编码 */
    private String corpCode;

    /** 对外展示类型：GROUP / MENU */
    public String getDisplayType() {
        return "#GROUP#".equals(type) ? "GROUP" : "MENU";
    }
}
