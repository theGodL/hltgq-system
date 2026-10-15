package com.qgyun.hltgq.hltgqsystem.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 管理端菜单树节点（生效结构视图：配置叠加后的实际展示结构）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminTreeNodeVO {

    /** 平台菜单 ID */
    private String menuId;

    /** 生效名称（配置改名优先） */
    private String name;

    /** 平台原名（供页面比对，判断是否被改过名） */
    private String originalName;

    /** 展示类型：GROUP / MENU */
    private String type;

    /** 生效显隐 */
    private Boolean visible;

    /** 锁定节点（如灌区一张图）：页面禁改，保存时后端强制忽略变更 */
    private Boolean locked;

    /** 固定显示节点（如菜单管理入口）：页面禁止隐藏，保存与渲染均强制可见 */
    private Boolean noHide;

    /** 平台父节点不在 hltgq 集合（挂在虚拟根展示） */
    private Boolean orphan;

    private List<AdminTreeNodeVO> children;
}
