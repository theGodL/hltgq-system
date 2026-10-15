package com.qgyun.hltgq.hltgqsystem.loginlog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsystem.auth.UserContext;
import com.qgyun.hltgq.hltgqsystem.loginlog.entity.LoginLog;
import com.qgyun.hltgq.hltgqsystem.loginlog.entity.LoginRequest;
import com.qgyun.hltgq.hltgqsystem.loginlog.mapper.LoginLogMapper;
import com.qgyun.hltgq.hltgqsystem.loginlog.mapper.LoginRequestMapper;
import com.qgyun.hltgq.hltgqsystem.loginlog.vo.LoginLogQueryRequest;
import com.qgyun.hltgq.hltgqsystem.mapper.ApaasUcUserMapper;
import com.qgyun.hltgq.hltgqsystem.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录日志服务（自建）。
 * <p>真实登录采集：
 * <ol>
 *   <li>Redis 会话扫描：平台登录后会在 Redis 生成 {@code <prefix><id>} 的会话 Hash，
 *       扫描到新会话即记一条登录（操作人取会话 userId，UA/终端取会话字段，登录时间取发现时刻）；</li>
 *   <li>请求侧回填：用户访问本服务时，用请求的 IP / User-Agent 回填该会话的登录记录；</li>
 *   <li>上报接口：登录入口可精确上报（含 IP / 登录方式 / 登录地点）。</li>
 * </ol>
 */
@Service
public class LoginLogService {

    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);

    /** 会话 Hash key 前缀 */
    @Value("${loginlog.session-key-prefix:dev_hltgq_session:}")
    private String sessionKeyPrefix;

    /** 登录事件与登录请求的时间匹配窗口（秒） */
    @Value("${loginlog.match-window-seconds:5}")
    private long matchWindowSeconds;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private LoginRequestMapper loginRequestMapper;

    @Autowired
    private ApaasUcUserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Autowired
    private IpRegionService ipRegionService;

    /** 本次启动已处理过的会话（避免每请求查库） */
    private final Set<String> touched = ConcurrentHashMap.newKeySet();

    /**
     * 扫描 Redis 会话，采集真实登录（去重）。
     *
     * @return 新增条数
     */
    public int collectFromRedis() {
        Set<String> keys = scanSessionKeys();
        int inserted = 0;
        for (String key : keys) {
            try {
                if (loginLogMapper.countBySessionId(key) > 0) {
                    continue;
                }
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                String userId = strip(entries.get("userId"));
                if (!StringUtils.hasText(userId)) {
                    continue;
                }
                String corp = strip(entries.get("corpCode"));
                String ua = strip(entries.get("UserAgent"));
                String terminal = strip(entries.get("TERMINAL"));
                String loginType = firstNonEmpty(strip(entries.get("loginType")), strip(entries.get("loginPlatform")));
                if (!StringUtils.hasText(terminal)) {
                    terminal = deriveTerminal(ua);
                }
                insertLogin(key, userId, corp, loginType, terminal, null, ua, null, LocalDateTime.now());
                inserted++;
            } catch (Exception e) {
                log.warn("采集会话登录失败 key={}：{}", key, e.getMessage());
            }
        }
        if (inserted > 0) {
            log.info("登录日志采集：新增 {} 条（扫描会话 {} 个）", inserted, keys.size());
        }
        return inserted;
    }

    /**
     * 请求侧回填：会话已存在则补 IP/UA/终端；不存在则记一条登录。
     */
    public void touch(String sessionId, UserContext user, String ip, String userAgent) {
        if (!StringUtils.hasText(sessionId) || user == null || !StringUtils.hasText(user.getUserId())) {
            return;
        }
        if (!touched.add(sessionId)) {
            return;
        }
        try {
            LoginLog existing = loginLogMapper.selectOne(
                    new QueryWrapper<LoginLog>().eq("session_id", sessionId).last("limit 1"));
            String terminal = deriveTerminal(userAgent);
            if (existing == null) {
                insertLogin(sessionId, user.getUserId(), user.getCorpCode(), "CORP", terminal, ip, userAgent, null, LocalDateTime.now());
            } else if (!StringUtils.hasText(existing.getIp())) {
                loginLogMapper.enrich(sessionId, ip, userAgent, terminal, ipRegionService.region(ip));
            }
        } catch (Exception e) {
            log.debug("登录日志回填失败 session={}：{}", sessionId, e.getMessage());
        }
    }

    /**
     * 上报接口：精确记录一条登录事件（来自平台认证日志或登录入口）。
     * <p>同会话已存在则补全缺失字段（操作人/终端/方式/时间），否则新增。
     */
    public void report(String sessionId, String operator, String loginType, String terminal,
                       String ip, String userAgent, String region, LocalDateTime loginAt) {
        if (!StringUtils.hasText(operator)) {
            return;
        }
        // 仅采集 hltgq 租户（会话前缀固定 dev_hltgq_session:）
        if (StringUtils.hasText(sessionId) && !sessionId.startsWith(sessionKeyPrefix)) {
            return;
        }
        if (StringUtils.hasText(sessionId) && loginLogMapper.countBySessionId(sessionId) > 0) {
            loginLogMapper.enrichAuth(sessionId, operator, resolveName(operator), loginType, terminal, loginAt);
        } else {
            insertLogin(sessionId, operator, "hltgq", loginType, terminal, ip, userAgent, region,
                    loginAt != null ? loginAt : LocalDateTime.now());
        }
        matchPending();
    }

    /**
     * 上报一条“登录请求”（来自中间件 nginx access.log）：真实 IP / UA / 时间。
     */
    public void reportRequest(String ip, String userAgent, LocalDateTime loginAt) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        LoginRequest req = new LoginRequest();
        req.setId(shortIdGenerator.nextUUID(null));
        req.setIp(ip);
        req.setUserAgent(truncate(userAgent, 1000));
        req.setLoginAt(loginAt != null ? loginAt : LocalDateTime.now());
        req.setMatched("0");
        req.setCreatedAt(LocalDateTime.now());
        loginRequestMapper.insert(req);
        matchPending();
    }

    /**
     * 将缺 IP 的登录记录与未合并的登录请求按时间就近合并（窗口 {@code loginlog.match-window-seconds} 秒）。
     *
     * @return 合并条数
     */
    public int matchPending() {
        List<LoginLog> logs = loginLogMapper.selectList(new QueryWrapper<LoginLog>()
                .and(w -> w.isNull("ip").or().eq("ip", ""))
                .isNotNull("login_at")
                .orderByAsc("login_at")
                .last("limit 5000"));
        if (logs.isEmpty()) {
            return 0;
        }
        long windowSeconds = Math.max(1, matchWindowSeconds);
        int matched = 0;
        for (LoginLog log : logs) {
            if (log.getLoginAt() == null) {
                continue;
            }
            // 仅在该条登录记录时间窗内找未匹配请求，避免被历史无关请求堵塞
            List<LoginRequest> candidates = loginRequestMapper.selectList(new QueryWrapper<LoginRequest>()
                    .eq("matched", "0")
                    .between("login_at", log.getLoginAt().minusSeconds(windowSeconds),
                            log.getLoginAt().plusSeconds(windowSeconds))
                    .last("limit 100"));
            if (candidates.isEmpty()) {
                continue;
            }
            LoginRequest best = null;
            long bestDiff = Long.MAX_VALUE;
            long logMs = toMs(log.getLoginAt());
            for (LoginRequest req : candidates) {
                if (req.getLoginAt() == null) {
                    continue;
                }
                long diff = Math.abs(toMs(req.getLoginAt()) - logMs);
                if (diff < bestDiff) {
                    best = req;
                    bestDiff = diff;
                }
            }
            if (best != null) {
                loginLogMapper.updateIpById(log.getId(), best.getIp(), best.getUserAgent(),
                        deriveTerminal(best.getUserAgent()), ipRegionService.region(best.getIp()));
                loginRequestMapper.markMatched(best.getId());
                matched++;
            }
        }
        if (matched > 0) {
            log.info("登录请求合并：补全 {} 条登录记录的 IP/终端/地点", matched);
        }
        return matched;
    }

    /**
     * 按 UA 补全缺失的登录终端（兜底）。
     */
    public int fixTerminalFromUa() {
        List<LoginLog> list = loginLogMapper.selectList(new QueryWrapper<LoginLog>()
                .and(w -> w.isNull("terminal").or().eq("terminal", ""))
                .isNotNull("user_agent").ne("user_agent", "")
                .last("limit 2000"));
        int n = 0;
        for (LoginLog l : list) {
            String t = deriveTerminal(l.getUserAgent());
            if (StringUtils.hasText(t)) {
                loginLogMapper.updateTerminal(l.getId(), t);
                n++;
            }
        }
        if (n > 0) {
            log.info("登录终端兜底：按 UA 补全 {} 条", n);
        }
        return n;
    }

    /**
     * 补齐缺失的登录地点（内网→"内网"，无法解析的公网→"未知"）。
     */
    public int refillRegion() {
        List<LoginLog> list = loginLogMapper.selectList(new QueryWrapper<LoginLog>()
                .and(w -> w.isNull("region").or().eq("region", ""))
                .isNotNull("ip").ne("ip", "")
                .last("limit 5000"));
        int n = 0;
        for (LoginLog l : list) {
            String r = ipRegionService.region(l.getIp());
            if (!StringUtils.hasText(r)) {
                r = IpRegionService.isPrivate(l.getIp()) ? "内网" : "未知";
            }
            loginLogMapper.updateRegion(l.getId(), r);
            n++;
        }
        if (n > 0) {
            log.info("登录地点补齐：{} 条", n);
        }
        return n;
    }

    private static long toMs(LocalDateTime t) {
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 分页查询登录日志（按登录时间倒序）。
     */
    public IPage<LoginLog> page(LoginLogQueryRequest q) {
        long pageNo = q.getPageNo() == null || q.getPageNo() < 1 ? 1 : q.getPageNo();
        long pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 200);
        Page<LoginLog> page = new Page<>(pageNo, pageSize);
        QueryWrapper<LoginLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like("operator_name", kw)
                    .or().like("ip", kw)
                    .or().like("session_id", kw)
                    .or().like("user_agent", kw));
        }
        if (StringUtils.hasText(q.getLoginType())) {
            wrapper.eq("login_type", q.getLoginType().trim());
        }
        if (StringUtils.hasText(q.getTerminal())) {
            wrapper.eq("terminal", q.getTerminal().trim());
        }
        if (StringUtils.hasText(q.getOperator())) {
            wrapper.eq("operator", q.getOperator().trim());
        }
        if (StringUtils.hasText(q.getStartTime())) {
            wrapper.apply("login_at >= to_timestamp({0}, 'YYYY-MM-DD HH24:MI:SS')", q.getStartTime().trim());
        }
        if (StringUtils.hasText(q.getEndTime())) {
            wrapper.apply("login_at <= to_timestamp({0}, 'YYYY-MM-DD HH24:MI:SS')", q.getEndTime().trim());
        }
        wrapper.orderByDesc("login_at");
        return loginLogMapper.selectPage(page, wrapper);
    }

    /** 扫描集群中所有会话 key（集群模式遍历各主节点） */
    @SuppressWarnings("unchecked")
    private Set<String> scanSessionKeys() {
        final byte[] pattern = (sessionKeyPrefix + "*").getBytes(StandardCharsets.UTF_8);
        return redisTemplate.execute((RedisCallback<Set<String>>) conn -> {
            Set<byte[]> raw;
            RedisConnection rc = conn;
            if (rc instanceof RedisClusterConnection) {
                raw = ((RedisClusterConnection) rc).keys(pattern);
            } else {
                raw = rc.keys(pattern);
            }
            Set<String> keys = new LinkedHashSet<>();
            if (raw != null) {
                for (byte[] k : raw) {
                    keys.add(new String(k, StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    /** 落库（解析操作人姓名） */
    private void insertLogin(String sessionId, String operatorId, String corpCode, String loginType,
                             String terminal, String ip, String userAgent, String region, LocalDateTime loginAt) {
        LoginLog entity = new LoginLog();
        entity.setId(shortIdGenerator.nextUUID(null));
        entity.setCorpCode(StringUtils.hasText(corpCode) ? corpCode : "hltgq");
        entity.setOperator(operatorId);
        entity.setOperatorName(resolveName(operatorId));
        entity.setLoginType(StringUtils.hasText(loginType) ? loginType : "CORP");
        entity.setTerminal(terminal);
        entity.setIp(ip);
        entity.setSessionId(sessionId);
        entity.setUserAgent(truncate(userAgent, 1000));
        entity.setRegion(StringUtils.hasText(region) ? region : ipRegionService.region(ip));
        entity.setLoginAt(loginAt);
        entity.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(entity);
    }

    private String resolveName(String operatorId) {
        try {
            List<Map<String, Object>> users = userMapper.selectUsersByIds(Collections.singletonList(operatorId));
            if (!users.isEmpty()) {
                Object name = users.get(0).get("name");
                if (name != null && StringUtils.hasText(String.valueOf(name))) {
                    return String.valueOf(name);
                }
                Object login = users.get(0).get("login_name");
                if (login != null) {
                    return String.valueOf(login);
                }
            }
        } catch (Exception e) {
            log.debug("解析操作人姓名失败 id={}：{}", operatorId, e.getMessage());
        }
        return operatorId;
    }

    /** 终端推断：微信 / 钉钉 / H5 / PC */
    public static String deriveTerminal(String ua) {
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        String s = ua.toLowerCase();
        if (s.contains("micromessenger")) {
            return "微信";
        }
        if (s.contains("dingtalk")) {
            return "钉钉";
        }
        if (s.contains("android") || s.contains("iphone") || s.contains("ipad") || s.contains("mobile")) {
            return "H5";
        }
        return "PC";
    }

    private static String firstNonEmpty(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    /** 剥离会话 Hash 值的 JSON 引号 */
    private static String strip(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
