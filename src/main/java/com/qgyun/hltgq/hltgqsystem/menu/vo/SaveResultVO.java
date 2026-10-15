package com.qgyun.hltgq.hltgqsystem.menu.vo;

import lombok.Data;

/**
 * 管理端整树保存结果。
 */
@Data
public class SaveResultVO {

    /** 提交节点总数 */
    private int total;

    /** 实际变更的配置行数（新增 + 更新 + 删除） */
    private int changed;
}
