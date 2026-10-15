package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理端整树保存请求：深度优先顺序的节点列表。
 */
@Data
public class MenuSaveRequest {

    /** 整树节点（DFS 顺序；同父内按出现先后） */
    private List<SaveNode> nodes;
}
