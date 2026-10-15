package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

import java.util.List;

/**
 * 展示端 overlay 兜底接口请求体：前端自传基础树（权限过滤后的原始结构），
 * 后端仅叠加展示配置。用于服务端转发平台接口不可用的降级路径。
 */
@Data
public class OverlayRequest {

    /** 基础菜单树（结构同展示端契约） */
    private List<RenderNodeVO> menus;
}
