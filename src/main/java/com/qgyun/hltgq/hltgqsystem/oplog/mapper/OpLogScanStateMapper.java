package com.qgyun.hltgq.hltgqsystem.oplog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLogScanState;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志扫描水位线 Mapper（按表读写，表数少，直接用 BaseMapper）。
 */
@Mapper
public interface OpLogScanStateMapper extends BaseMapper<OpLogScanState> {
}
