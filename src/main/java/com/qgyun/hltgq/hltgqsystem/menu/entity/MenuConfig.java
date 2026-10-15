package com.qgyun.hltgq.hltgqsystem.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单展示配置（自建表 t_auto_hltgq_menu_config）。
 * <p>旁路配置：不修改平台菜单库 t_apaas_sys_menu，仅在菜单渲染时叠加差异。
 * 稀疏存储：一行 = 一个菜单与平台现状的差异，无差异的菜单不落行。
 * <p>字段语义：
 * <ul>
 *   <li>parentId：改挂目标；'#ROOT#' 表示提级到顶层；空表示维持平台原父</li>
 *   <li>sortNo：同父内排序号（仅当该父下顺序被调整过时存在）</li>
 *   <li>displayName：改名覆盖；空表示用平台原名</li>
 *   <li>visible：1=显示（默认）0=隐藏（节点保留在结构中，仅标记不可见）</li>
 * </ul>
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_menu_config\"")
public class MenuConfig {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    /** 平台菜单 ID（t_apaas_sys_menu.id，唯一键） */
    @TableField("\"menu_id\"")
    private String menuId;

    /** 改挂目标：'#ROOT#'=顶层 / 其他菜单 id / 空=维持平台原父 */
    @TableField("\"parent_id\"")
    private String parentId;

    /** 同父内排序号 */
    @TableField("\"sort_no\"")
    private Integer sortNo;

    /** 改名覆盖（空=用平台原名） */
    @TableField("\"display_name\"")
    private String displayName;

    /** 是否显示：1=显示 0=隐藏 */
    @TableField("\"visible\"")
    private Integer visible;

    @TableField("\"created_at\"")
    private LocalDateTime createdAt;

    @TableField("\"updated_at\"")
    private LocalDateTime updatedAt;

    /** 最后修改人（登录名） */
    @TableField("\"updated_by\"")
    private String updatedBy;
}
