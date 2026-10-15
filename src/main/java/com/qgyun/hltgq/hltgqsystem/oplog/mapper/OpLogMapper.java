package com.qgyun.hltgq.hltgqsystem.oplog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper（分页查询在 Service 用 QueryWrapper 组装，以复用实体列映射）。
 */
@Mapper
public interface OpLogMapper extends BaseMapper<OpLog> {
}
