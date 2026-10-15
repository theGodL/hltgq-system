package com.qgyun.hltgq.hltgqsystem.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qgyun.hltgq.hltgqsystem.menu.entity.MenuConfig;
import com.qgyun.hltgq.hltgqsystem.menu.entity.PlatformMenu;
import com.qgyun.hltgq.hltgqsystem.menu.mapper.MenuConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单模块内部共用工具（管理端 / 展示端共用）。
 */
final class MenuSupport {

    private static final Logger log = LoggerFactory.getLogger(MenuSupport.class);

    /** 虚拟顶层：配置 parent_id 取值与内部序列键 */
    static final String ROOT = "#ROOT#";

    /** 配置版本格式 */
    private static final DateTimeFormatter VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private MenuSupport() {
    }

    /** 解析逗号分隔的菜单 ID 配置（锁定 / 固定显示 / 管理页排除共用） */
    static Set<String> parseIdSet(String raw) {
        Set<String> set = new HashSet<>();
        if (!StringUtils.hasText(raw)) {
            return set;
        }
        for (String part : raw.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return set;
    }

    /** 平台菜单列表 → id 索引 */
    static Map<String, PlatformMenu> indexById(List<PlatformMenu> all) {
        Map<String, PlatformMenu> map = new HashMap<>();
        for (PlatformMenu n : all) {
            map.put(n.getId(), n);
        }
        return map;
    }

    /** 读取 hltgq 租户全部配置行 → menuId 索引 */
    static Map<String, MenuConfig> loadConfigMap(MenuConfigMapper mapper) {
        Map<String, MenuConfig> map = new HashMap<>();
        List<MenuConfig> list;
        try {
            LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MenuConfig::getCorpCode, "hltgq");
            list = mapper.selectList(wrapper);
        } catch (Exception e) {
            // 容错：配置表未建（schema SQL 未执行）时按空配置处理，
            // 展示端退化为平台原样输出、服务可先行部署；写操作仍会因缺表失败并报错
            log.warn("菜单展示配置表不可用（是否未执行 menu_config_schema.sql？），按空配置处理: {}", e.getMessage());
            return map;
        }
        for (MenuConfig c : list) {
            map.put(c.getMenuId(), c);
        }
        return map;
    }

    /** 配置版本号：最大 updated_at（yyyyMMddHHmmss）；无配置返回空串 */
    static String versionOf(Collection<MenuConfig> cfgs) {
        LocalDateTime max = null;
        for (MenuConfig c : cfgs) {
            if (c.getUpdatedAt() != null && (max == null || c.getUpdatedAt().isAfter(max))) {
                max = c.getUpdatedAt();
            }
        }
        return max == null ? "" : max.format(VERSION_FORMATTER);
    }

    /** Double 比较（null 排最后） */
    static int compareDoubleNullLast(Double a, Double b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return Double.compare(a, b);
    }

    /** 锁定节点在其父序列中提前到最前（保持相对顺序），保证"灌区一张图"始终第一 */
    static void promoteLocked(List<String> seq, Set<String> locked) {
        if (seq == null || seq.isEmpty() || locked.isEmpty()) {
            return;
        }
        List<String> front = new ArrayList<>();
        for (String id : seq) {
            if (locked.contains(id)) {
                front.add(id);
            }
        }
        if (front.isEmpty() || front.size() == seq.size()) {
            return;
        }
        seq.removeAll(front);
        seq.addAll(0, front);
    }
}
