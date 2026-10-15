package com.qgyun.hltgq.hltgqsystem.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsystem.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsystem.oplog.config.MonitoredTableRegistry;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLog;
import com.qgyun.hltgq.hltgqsystem.oplog.service.FieldDictService;
import com.qgyun.hltgq.hltgqsystem.oplog.service.OpLogQueryService;
import com.qgyun.hltgq.hltgqsystem.oplog.service.OpLogScanService;
import com.qgyun.hltgq.hltgqsystem.oplog.vo.OpLogQueryRequest;
import com.qgyun.hltgq.hltgqsystem.oplog.vo.ScanResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 操作日志监控接口（仅系统管理员可访问，@RequireAdmin）。
 */
@RestController
@RequestMapping("/op-log")
@RequireAdmin
public class OpLogController {

    @Autowired
    private OpLogQueryService queryService;

    @Autowired
    private OpLogScanService scanService;

    @Autowired
    private FieldDictService fieldDictService;

    /** 分页查询操作日志 */
    @GetMapping("/list")
    public IPage<OpLog> list(OpLogQueryRequest request) {
        return queryService.page(request);
    }

    /** 日志详情 */
    @GetMapping("/{id}")
    public OpLog detail(@PathVariable("id") String id) {
        return queryService.detail(id);
    }

    /** 分组下拉数据 */
    @GetMapping("/groups")
    public List<MonitoredTableRegistry.GroupInfo> groups() {
        return queryService.groups();
    }

    /** 字典：{ fields:{字段码:中文名}, enums:{字段码:{值码:中文标签}} }，供详情描述翻译 */
    @GetMapping("/dict")
    public Map<String, Object> dict(@RequestParam("tableName") String tableName) {
        return fieldDictService.dictOf(tableName);
    }

    /** 手动触发扫描（增量） */
    @PostMapping("/scan")
    public ScanResultVO scan() {
        return scanService.scanAll();
    }
}
