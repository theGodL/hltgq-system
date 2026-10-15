package com.qgyun.hltgq.hltgqsystem.oplog.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 被监控表注册表：加载 classpath:oplog-tables.json，扁平化启用分组下的表清单。
 * <p>配置化接入：新增/启停监控表只改 JSON，无需改代码。
 */
@Component
public class MonitoredTableRegistry {

    private static final Logger log = LoggerFactory.getLogger(MonitoredTableRegistry.class);

    /** 水位线时间格式（与 to_char('YYYY-MM-DD HH24:MI:SS.MS') 输出一致） */
    public static final DateTimeFormatter WATERMARK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Value("${oplog.config-location:classpath:oplog-tables.json}")
    private Resource configResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 全量配置 */
    private OpLogConfig config = new OpLogConfig();

    /** 启用的被监控表（扁平） */
    private final List<MonitoredTable> enabledTables = new ArrayList<>();

    @PostConstruct
    public void load() {
        try (InputStream in = configResource.getInputStream()) {
            config = objectMapper.readValue(in, OpLogConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载操作日志监控配置失败: " + e.getMessage(), e);
        }
        enabledTables.clear();
        if (config.getGroups() != null) {
            for (OpLogConfig.Group group : config.getGroups()) {
                if (!group.isEnabled() || group.getTables() == null) {
                    continue;
                }
                for (MonitoredTable table : group.getTables()) {
                    table.setGroupName(group.getGroupName());
                    table.setAppName(group.getAppName());
                    enabledTables.add(table);
                }
            }
        }
        log.info("操作日志监控配置加载完成：启用 {} 张表，扫描开关={}，系统操作人={}，首次回溯 {} 天",
                enabledTables.size(), config.isScanEnabled(), config.getSystemOperators(),
                config.getInitialLookbackDays());
    }

    public List<MonitoredTable> enabledTables() {
        return Collections.unmodifiableList(enabledTables);
    }

    public Set<String> systemOperators() {
        List<String> ops = config.getSystemOperators();
        if (ops == null) {
            return Collections.emptySet();
        }
        return ops.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 默认起始水位线（首次扫描回溯期） */
    public String defaultWatermark() {
        int days = config.getInitialLookbackDays() > 0 ? config.getInitialLookbackDays() : 30;
        return LocalDateTime.now().minusDays(days).format(WATERMARK_FORMAT);
    }

    public boolean scanEnabled() {
        return config.isScanEnabled();
    }

    public String scanCron() {
        return StringUtils.hasText(config.getScanCron()) ? config.getScanCron() : "0 0/3 * * * ?";
    }

    /** 分组清单（供页面筛选下拉，含未启用分组） */
    public List<GroupInfo> groups() {
        List<GroupInfo> list = new ArrayList<>();
        if (config.getGroups() != null) {
            for (OpLogConfig.Group group : config.getGroups()) {
                GroupInfo info = new GroupInfo();
                info.setGroupName(group.getGroupName());
                info.setAppName(group.getAppName());
                info.setEnabled(group.isEnabled());
                info.setTableCount(group.getTables() == null ? 0 : group.getTables().size());
                list.add(info);
            }
        }
        return list;
    }

    /** 分组信息（页面下拉） */
    @Data
    public static class GroupInfo {
        private String groupName;
        private String appName;
        private boolean enabled;
        private int tableCount;
    }

    /** 配置根对象 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpLogConfig {
        /** 首次扫描回溯天数 */
        private int initialLookbackDays = 30;
        /** 扫描 cron */
        private String scanCron = "0 0/3 * * * ?";
        /** 扫描总开关 */
        private boolean scanEnabled = true;
        /** 机器操作人（不记录）：如 SYSTEM / model-service */
        private List<String> systemOperators;
        /** 分组 */
        private List<Group> groups;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Group {
            private String groupName;
            private String appName;
            private boolean enabled;
            private List<MonitoredTable> tables;
        }
    }
}
