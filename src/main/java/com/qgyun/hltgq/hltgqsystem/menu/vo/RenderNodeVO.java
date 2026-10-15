package com.qgyun.hltgq.hltgqsystem.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 展示端菜单节点（契约最小字段：id / name / type / visible / children）。
 * <p>既用于渲染输出，也用于 overlay 兜底接口的输入接收
 * （输入通常来自主题壳缓存的上一版快照）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RenderNodeVO {

    /** 平台菜单 ID */
    private String id;

    /** 生效名称 */
    private String name;

    /** 类型：GROUP / MENU */
    private String type;

    /** 是否可见：隐藏节点仍下发（visible=false），由前端决定不渲染 */
    private Boolean visible;

    private List<RenderNodeVO> children;
}
