package com.qgyun.hltgq.hltgqsystem.oplog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.mapper.FieldDictMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字段中文名字典服务：
 * <ul>
 *   <li>fields：低代码表 随机字段码 → 中文标题（t_lcode_model_field）；平台内部表返回内置常见列名；</li>
 *   <li>enums：枚举字段 → 值编码 → 中文标签（t_lcode_option_group.config_json.options）。</li>
 * </ul>
 * 进程内缓存。
 */
@Service
public class FieldDictService {

    private static final Logger log = LoggerFactory.getLogger(FieldDictService.class);

    private static final String PREFIX = "t_auto_hltgq_";
    private static final Pattern ENUM_PATTERN = Pattern.compile("QxEnum\\[([^]]+)\\]");

    @Autowired
    private FieldDictMapper fieldDictMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** tableName → {fields, enums} */
    private final ConcurrentHashMap<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    /** 枚举选项组缓存 scopeId → (value → label) */
    private final ConcurrentHashMap<String, Map<String, String>> optionCache = new ConcurrentHashMap<>();

    private static final Map<String, String> PLATFORM_DICT = new HashMap<>();

    static {
        PLATFORM_DICT.put("id", "主键");
        PLATFORM_DICT.put("corp_code", "企业编码");
        PLATFORM_DICT.put("name", "名称");
        PLATFORM_DICT.put("code", "编码");
        PLATFORM_DICT.put("status", "状态");
        PLATFORM_DICT.put("pwd", "密码");
        PLATFORM_DICT.put("login_name", "登录名");
        PLATFORM_DICT.put("description", "描述");
        PLATFORM_DICT.put("parent_id", "上级");
        PLATFORM_DICT.put("type", "类型");
        PLATFORM_DICT.put("pages", "功能权限");
        PLATFORM_DICT.put("datas", "数据权限");
        PLATFORM_DICT.put("role_id", "角色");
        PLATFORM_DICT.put("app_id", "应用");
        PLATFORM_DICT.put("page_id", "页面");
        PLATFORM_DICT.put("model_id", "模型");
        PLATFORM_DICT.put("rel_id", "关联主体");
        PLATFORM_DICT.put("biz_id", "关联对象");
        PLATFORM_DICT.put("field_id", "关联字段");
        PLATFORM_DICT.put("nature_order", "排序");
        PLATFORM_DICT.put("admin_id", "管理员");
        PLATFORM_DICT.put("administrator", "管理员");
    }

    /**
     * 取某表的字典：{ fields: {字段码:中文名}, enums: {字段码:{值码:中文标签}} }。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dictOf(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return empty();
        }
        Map<String, Object> cached = cache.get(tableName);
        if (cached != null) {
            return cached;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, Map<String, String>> enums = new LinkedHashMap<>();
        if (tableName.startsWith("t_apaas_")) {
            fields.putAll(PLATFORM_DICT);
        } else {
            try {
                List<Map<String, Object>> rows = fieldDictMapper.selectFieldDict(tableName, candidateCodes(tableName));
                for (Map<String, Object> row : rows) {
                    String k = str(row.get("k"));
                    String t = str(row.get("t"));
                    if (k == null || t == null) {
                        continue;
                    }
                    fields.put(k, t);
                    String scope = extractEnumScope(str(row.get("c")));
                    if (scope != null) {
                        Map<String, String> opts = optionMap(scope);
                        if (!opts.isEmpty()) {
                            enums.put(k, opts);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("字段字典查询失败 table={}：{}", tableName, e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        result.put("enums", enums);
        cache.put(tableName, result);
        return result;
    }

    private Map<String, Object> empty() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fields", Collections.emptyMap());
        r.put("enums", Collections.emptyMap());
        return r;
    }

    /** 从字段 config_json 的 propType 提取枚举选项组 id：QxEnum[app.scope] → scope */
    private String extractEnumScope(String configJson) {
        if (configJson == null) {
            return null;
        }
        Matcher m = ENUM_PATTERN.matcher(configJson);
        if (!m.find()) {
            return null;
        }
        String inner = m.group(1);
        int dot = inner.lastIndexOf('.');
        String scope = dot >= 0 ? inner.substring(dot + 1) : inner;
        return scope.isEmpty() ? null : scope;
    }

    /** 枚举选项组：value → label */
    private Map<String, String> optionMap(String scopeId) {
        Map<String, String> cached = optionCache.get(scopeId);
        if (cached != null) {
            return cached;
        }
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String cfg = fieldDictMapper.selectOptionGroup(scopeId);
            if (cfg != null) {
                JsonNode options = objectMapper.readTree(cfg).get("options");
                if (options != null && options.isArray()) {
                    for (JsonNode o : options) {
                        String v = o.path("value").asText(null);
                        String label = o.path("label").asText(null);
                        if (v != null && label != null) {
                            map.put(v, label);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("枚举选项查询失败 scope={}：{}", scopeId, e.getMessage());
        }
        optionCache.put(scopeId, map);
        return map;
    }

    private List<String> candidateCodes(String tableName) {
        List<String> codes = new ArrayList<>();
        String rest = tableName.startsWith(PREFIX) ? tableName.substring(PREFIX.length()) : tableName;
        codes.add(rest);
        int idx = rest.indexOf('_');
        if (idx > 0 && idx + 1 < rest.length()) {
            codes.add(rest.substring(idx + 1));
        }
        return codes;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? null : s;
    }
}
