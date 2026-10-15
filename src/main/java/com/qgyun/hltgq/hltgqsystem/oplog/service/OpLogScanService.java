package com.qgyun.hltgq.hltgqsystem.oplog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qgyun.hltgq.hltgqsystem.mapper.ApaasUcUserMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.config.MonitoredTable;
import com.qgyun.hltgq.hltgqsystem.oplog.config.MonitoredTableRegistry;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLog;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLogScanState;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.OpLogMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.OpLogScanMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.OpLogScanStateMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.OpLogSnapshotMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.vo.ScanResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 操作日志扫描服务：按监控清单增量扫描业务/平台表的审计列 + 快照 diff，生成操作日志。
 * <p>口径：仅记录人工操作——按 created_by/updated_by 过滤机器操作人（SYSTEM 等）。
 * <ul>
 *   <li>新增：created_at &gt; 水位线 → INSERT，操作人 created_by；</li>
 *   <li>修改：created_at ≤ 水位线 &lt; updated_at → UPDATE，操作人 updated_by，before 取上次快照；</li>
 *   <li>硬删除：上次快照存在、本次消失 → DELETE（仅 trackDelete 表）；</li>
 *   <li>软删除：softDeleteColumn 翻转为删除值 → DELETE；</li>
 *   <li>脱敏：maskFields 字段值以 *** 落库（仅保留"发生变更"事件）。</li>
 * </ul>
 * <p>不变量：无论是否产生日志，命中行的快照都会更新，保证后续 UPDATE/DELETE 的 before 正确。
 */
@Service
public class OpLogScanService {

    private static final Logger log = LoggerFactory.getLogger(OpLogScanService.class);

    /** 变更摘要忽略的审计列 */
    private static final Set<String> AUDIT_KEYS = new HashSet<>();

    static {
        AUDIT_KEYS.add("created_at");
        AUDIT_KEYS.add("created_by");
        AUDIT_KEYS.add("updated_at");
        AUDIT_KEYS.add("updated_by");
        AUDIT_KEYS.add("corp_code");
    }

    private static final String OP_INSERT = "INSERT";
    private static final String OP_UPDATE = "UPDATE";
    private static final String OP_DELETE = "DELETE";
    private static final String MASK = "***";

    @Autowired
    private OpLogScanMapper scanMapper;

    @Autowired
    private OpLogMapper opLogMapper;

    @Autowired
    private OpLogScanStateMapper stateMapper;

    @Autowired
    private OpLogSnapshotMapper snapshotMapper;

    @Autowired
    private ApaasUcUserMapper userMapper;

    @Autowired
    private MonitoredTableRegistry registry;

    @Autowired
    private com.qgyun.hltgq.hltgqsystem.model.util.ShortIdGenerator shortIdGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    /** 防重入锁（定时任务 + 手动触发） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 扫描全部启用表。
     */
    public ScanResultVO scanAll() {
        ScanResultVO result = new ScanResultVO();
        if (!registry.scanEnabled()) {
            result.setMessage("操作日志扫描开关已关闭（oplog.scan.enabled / 配置 scanEnabled=false）");
            return result;
        }
        if (!running.compareAndSet(false, true)) {
            result.setMessage("已有扫描任务在执行，本次跳过");
            return result;
        }
        try {
            List<MonitoredTable> tables = registry.enabledTables();
            result.setExecuted(true);
            result.setTables(tables.size());
            int ins = 0, upd = 0, del = 0;
            for (MonitoredTable table : tables) {
                try {
                    int[] counts = scanTable(table);
                    ins += counts[0];
                    upd += counts[1];
                    del += counts[2];
                } catch (Exception e) {
                    log.error("扫描表 {} 失败：{}", table.getTableName(), e.getMessage(), e);
                }
            }
            result.setInserts(ins);
            result.setUpdates(upd);
            result.setDeletes(del);
            result.setMessage(String.format("扫描完成：%d 张表，新增 %d / 修改 %d / 删除 %d",
                    tables.size(), ins, upd, del));
            log.info("操作日志扫描完成：{}", result.getMessage());
        } finally {
            running.set(false);
        }
        return result;
    }

    /**
     * 扫描单表，返回 [insert, update, delete] 计数。
     */
    private int[] scanTable(MonitoredTable table) throws Exception {
        String tableName = table.getTableName();
        String corpFilter = table.getCorpFilter();
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(MonitoredTableRegistry.WATERMARK_FORMAT);

        OpLogScanState state = stateMapper.selectById(tableName);
        String watermark = (state != null && state.getWatermark() != null && !state.getWatermark().isEmpty())
                ? state.getWatermark() : registry.defaultWatermark();

        List<String> rowJsons = scanMapper.selectChangedRows(tableName, watermark, corpFilter);
        Set<String> systemOps = registry.systemOperators();

        List<OpLog> pending = new ArrayList<>();
        Set<String> operatorIds = new HashSet<>();
        String maxOpAt = null;

        for (String json : rowJsons) {
            JsonNode node;
            try {
                node = objectMapper.readTree(json);
            } catch (Exception e) {
                log.warn("解析行 JSON 失败 table={}：{}", tableName, e.getMessage());
                continue;
            }
            String bizId = text(node, "id");
            if (bizId == null) {
                continue;
            }
            String createdBy = text(node, "created_by");
            String updatedBy = text(node, "updated_by");
            String corpCode = text(node, "corp_code");
            String createdAt = normTime(text(node, "created_at"));
            String updatedAt = normTime(text(node, "updated_at"));

            String before = snapshotMapper.selectRowJson(tableName, bizId);
            boolean softDeleted = isSoftDeleted(node, table);

            String opType = null;
            String opAt = null;
            String operator = null;
            if (softDeleted) {
                if (updatedAt != null && updatedAt.compareTo(watermark) > 0) {
                    opType = OP_DELETE;
                    opAt = updatedAt;
                    operator = updatedBy;
                }
            } else if (createdAt != null && createdAt.compareTo(watermark) > 0) {
                opType = OP_INSERT;
                opAt = laterOf(createdAt, updatedAt);
                operator = createdBy;
            } else if (updatedAt != null && updatedAt.compareTo(watermark) > 0) {
                opType = OP_UPDATE;
                opAt = updatedAt;
                operator = updatedBy;
            }

            if (opAt != null && (maxOpAt == null || opAt.compareTo(maxOpAt) > 0)) {
                maxOpAt = opAt;
            }

            if (opType != null && isHuman(operator, systemOps)) {
                OpLog entry = new OpLog();
                entry.setCorpCode(corpCode != null ? corpCode : "hltgq");
                entry.setGroupName(table.getGroupName());
                entry.setTableName(tableName);
                entry.setFormName(table.getFormName());
                entry.setBizId(bizId);
                entry.setOpType(opType);
                entry.setOpAt(parseTime(opAt));
                entry.setOperatorId(operator);
                entry.setOperatorName(operator);
                entry.setCreatedAt(now);
                if (OP_DELETE.equals(opType)) {
                    entry.setBeforeJson(mask(before != null ? before : json, table));
                    entry.setAfterJson(null);
                } else if (OP_UPDATE.equals(opType)) {
                    entry.setBeforeJson(mask(before, table));
                    entry.setAfterJson(mask(json, table));
                    entry.setChangeFields(changedFields(before, node));
                } else {
                    entry.setBeforeJson(null);
                    entry.setAfterJson(mask(json, table));
                }
                pending.add(entry);
                operatorIds.add(operator);
            }

            // 快照维护：软删后移除，其余更新为当前（未脱敏，供 diff）
            if (softDeleted) {
                snapshotMapper.deleteByBiz(tableName, bizId);
            } else {
                snapshotMapper.upsertSnapshot(genId(), tableName, bizId, json);
            }
        }

        // 硬删除识别（仅 trackDelete 表）
        int deleteCount = 0;
        if (table.isTrackDelete()) {
            List<String> currentIds = scanMapper.selectAllIds(tableName, corpFilter);
            Set<String> current = new HashSet<>(currentIds);
            List<Map<String, Object>> snapshots = snapshotMapper.selectByTable(tableName);
            for (Map<String, Object> snap : snapshots) {
                String bizId = str(snap.get("biz_id"));
                if (bizId == null || current.contains(bizId)) {
                    continue;
                }
                String rowJson = str(snap.get("row_json"));
                JsonNode node = rowJson == null ? null : objectMapper.readTree(rowJson);
                String operator = node == null ? null : firstHumanOperator(node, systemOps);
                OpLog entry = new OpLog();
                entry.setCorpCode(node == null ? "hltgq" : text(node, "corp_code"));
                entry.setGroupName(table.getGroupName());
                entry.setTableName(tableName);
                entry.setFormName(table.getFormName());
                entry.setBizId(bizId);
                entry.setOpType(OP_DELETE);
                entry.setOpAt(now);
                entry.setOperatorId(operator);
                entry.setOperatorName(operator);
                entry.setBeforeJson(mask(rowJson, table));
                entry.setAfterJson(null);
                entry.setCreatedAt(now);
                pending.add(entry);
                if (operator != null) {
                    operatorIds.add(operator);
                }
                snapshotMapper.deleteByBiz(tableName, bizId);
                deleteCount++;
            }
        }

        Map<String, String> names = resolveNames(operatorIds);
        int ins = 0, upd = 0, del = 0;
        for (OpLog entry : pending) {
            if (entry.getOperatorId() != null) {
                entry.setOperatorName(names.getOrDefault(entry.getOperatorId(), entry.getOperatorId()));
            }
            opLogMapper.insert(entry);
            if (OP_INSERT.equals(entry.getOpType())) {
                ins++;
            } else if (OP_UPDATE.equals(entry.getOpType())) {
                upd++;
            } else if (OP_DELETE.equals(entry.getOpType())) {
                del++;
            }
        }

        String newWatermark = maxOpAt != null ? maxOpAt : watermark;
        OpLogScanState newState = new OpLogScanState();
        newState.setTableName(tableName);
        newState.setWatermark(newWatermark);
        newState.setLastScanAt(now);
        if (state == null) {
            stateMapper.insert(newState);
        } else {
            stateMapper.updateById(newState);
        }

        if (ins + upd + del > 0) {
            log.info("表 {}（{}）：新增 {} / 修改 {} / 删除 {}，水位线 {} -> {}",
                    tableName, table.getFormName(), ins, upd, del, watermark, newWatermark);
        }
        return new int[]{ins, upd, del};
    }

    /** 操作人是否为人工（非空且不在机器操作人清单内） */
    private boolean isHuman(String operator, Set<String> systemOps) {
        if (operator == null || operator.trim().isEmpty()) {
            return false;
        }
        return !systemOps.contains(operator.trim());
    }

    /** 删除归属人：优先 updated_by，其次 created_by，命中机器操作人返回 null */
    private String firstHumanOperator(JsonNode node, Set<String> systemOps) {
        String updatedBy = text(node, "updated_by");
        String createdBy = text(node, "created_by");
        if (isHuman(updatedBy, systemOps)) {
            return updatedBy;
        }
        if (isHuman(createdBy, systemOps)) {
            return createdBy;
        }
        return null;
    }

    /** 软删除判定 */
    private boolean isSoftDeleted(JsonNode node, MonitoredTable table) {
        String col = table.getSoftDeleteColumn();
        if (col == null || col.isEmpty() || table.getSoftDeleteValues() == null) {
            return false;
        }
        JsonNode v = node.get(col);
        if (v == null || v.isNull()) {
            return false;
        }
        String val = v.asText().trim();
        if (val.isEmpty()) {
            return false;
        }
        for (String d : table.getSoftDeleteValues()) {
            if (d != null && d.trim().equalsIgnoreCase(val)) {
                return true;
            }
        }
        return false;
    }

    /** 敏感字段脱敏：将指定字段值替换为 *** */
    private String mask(String json, MonitoredTable table) {
        if (json == null || table.getMaskFields() == null || table.getMaskFields().isEmpty()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!(node instanceof ObjectNode)) {
                return json;
            }
            ObjectNode obj = (ObjectNode) node;
            for (String field : table.getMaskFields()) {
                if (obj.has(field) && !obj.get(field).isNull()) {
                    obj.put(field, MASK);
                }
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    /** 批量解析操作人姓名（id → name，name 空则回退 login_name） */
    private Map<String, String> resolveNames(Set<String> ids) {
        Map<String, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        List<Map<String, Object>> users = userMapper.selectUsersByIds(new ArrayList<>(ids));
        for (Map<String, Object> user : users) {
            String id = str(user.get("id"));
            if (id == null) {
                continue;
            }
            String name = str(user.get("name"));
            if (name == null) {
                name = str(user.get("login_name"));
            }
            names.put(id, name != null ? name : id);
        }
        return names;
    }

    /** 计算变更字段摘要（对比 before/after，忽略审计列；用未脱敏值判定） */
    private String changedFields(String beforeJson, JsonNode after) {
        if (beforeJson == null || after == null) {
            return null;
        }
        try {
            JsonNode before = objectMapper.readTree(beforeJson);
            List<String> changed = new ArrayList<>();
            after.fieldNames().forEachRemaining(key -> {
                if (AUDIT_KEYS.contains(key)) {
                    return;
                }
                JsonNode b = before.get(key);
                JsonNode a = after.get(key);
                if (b == null ? a != null : !b.equals(a)) {
                    changed.add(key);
                }
            });
            return changed.isEmpty() ? null : String.join(",", changed);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取两个归一化时间字符串中较晚者 */
    private static String laterOf(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** JSON 字段取值（缺失/null 返回 null） */
    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** 时间字符串归一为 yyyy-MM-dd HH:mm:ss.SSS（row_to_json 输出含微秒，截断到毫秒） */
    private static String normTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.trim().replace('T', ' ');
        if (s.length() >= 23) {
            return s.substring(0, 23);
        }
        if (s.length() >= 19) {
            return s.substring(0, 19) + ".000";
        }
        return s;
    }

    /** 归一字符串 → LocalDateTime（解析失败返回 null） */
    private static LocalDateTime parseTime(String normalized) {
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized, MonitoredTableRegistry.WATERMARK_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? null : s;
    }

    /** 生成 22 位短 ID */
    private String genId() {
        return shortIdGenerator.nextUUID(null);
    }
}
