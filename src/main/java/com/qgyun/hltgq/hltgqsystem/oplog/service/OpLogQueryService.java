package com.qgyun.hltgq.hltgqsystem.oplog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsystem.oplog.config.MonitoredTableRegistry;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLog;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.OpLogMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.vo.OpLogQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志查询服务。
 */
@Service
public class OpLogQueryService {

    @Autowired
    private OpLogMapper opLogMapper;

    @Autowired
    private MonitoredTableRegistry registry;

    /**
     * 分页查询操作日志（按操作时间倒序）。
     */
    public IPage<OpLog> page(OpLogQueryRequest q) {
        long pageNo = q.getPageNo() == null || q.getPageNo() < 1 ? 1 : q.getPageNo();
        long pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 200);
        Page<OpLog> page = new Page<>(pageNo, pageSize);

        QueryWrapper<OpLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(q.getGroupName())) {
            wrapper.eq("group_name", q.getGroupName().trim());
        }
        if (StringUtils.hasText(q.getTableName())) {
            wrapper.eq("table_name", q.getTableName().trim());
        }
        if (StringUtils.hasText(q.getOpType())) {
            wrapper.eq("op_type", q.getOpType().trim());
        }
        if (StringUtils.hasText(q.getOperatorId())) {
            wrapper.eq("operator_id", q.getOperatorId().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like("biz_id", kw)
                    .or().like("operator_name", kw)
                    .or().like("form_name", kw));
        }
        if (StringUtils.hasText(q.getStartTime())) {
            wrapper.apply("op_at >= to_timestamp({0}, 'YYYY-MM-DD HH24:MI:SS')", q.getStartTime().trim());
        }
        if (StringUtils.hasText(q.getEndTime())) {
            wrapper.apply("op_at <= to_timestamp({0}, 'YYYY-MM-DD HH24:MI:SS')", q.getEndTime().trim());
        }
        wrapper.orderByDesc("op_at");
        return opLogMapper.selectPage(page, wrapper);
    }

    /**
     * 单条日志详情。
     */
    public OpLog detail(String id) {
        return opLogMapper.selectById(id);
    }

    /**
     * 分组下拉数据（含未启用分组，便于后续放开）。
     */
    public List<MonitoredTableRegistry.GroupInfo> groups() {
        return registry.groups();
    }
}
