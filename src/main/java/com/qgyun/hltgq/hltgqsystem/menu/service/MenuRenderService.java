package com.qgyun.hltgq.hltgqsystem.menu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsystem.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsystem.auth.UnauthorizedException;
import com.qgyun.hltgq.hltgqsystem.menu.PlatformApiException;
import com.qgyun.hltgq.hltgqsystem.menu.entity.MenuConfig;
import com.qgyun.hltgq.hltgqsystem.menu.entity.PlatformMenu;
import com.qgyun.hltgq.hltgqsystem.menu.mapper.MenuConfigMapper;
import com.qgyun.hltgq.hltgqsystem.menu.mapper.PlatformMenuMapper;
import com.qgyun.hltgq.hltgqsystem.menu.vo.MenuTreeVO;
import com.qgyun.hltgq.hltgqsystem.menu.vo.OverlayRequest;
import com.qgyun.hltgq.hltgqsystem.menu.vo.RenderNodeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单渲染服务（展示端，供主题壳调用）。
 * <p>数据流：基础树（服务端带会话转发平台菜单接口，权限同源过滤）→ 叠加旁路配置
 * （改名 / 改挂 / 排序 / 显隐）→ 输出契约结构 {version, menus}。
 * <ul>
 *   <li>权限防护：配置只能作用于基础集合内的节点（改挂目标不在集合时回退原始位置），
 *       配置无法把用户无权的菜单"提权"出来</li>
 *   <li>隐藏节点仍下发（visible=false），由前端决定不渲染</li>
 *   <li>锁定节点（如灌区一张图）恒为顶层第一位；固定显示节点（如菜单管理入口）恒为可见</li>
 *   <li>缺失配置时无任何影响，输出即基础树原样</li>
 *   <li>配置表不存在 / 为空时输出即基础树原样，服务可先行部署</li>
 * </ul>
 */
@Service
public class MenuRenderService {

    private static final Logger log = LoggerFactory.getLogger(MenuRenderService.class);

    @Autowired
    private PlatformMenuMapper platformMenuMapper;

    @Autowired
    private MenuConfigMapper menuConfigMapper;

    @Autowired
    private SessionContextService sessionContextService;

    @Autowired
    private RestTemplate platformRestTemplate;

    /** 平台菜单接口（按登录用户下发可见菜单） */
    @Value("${menu.platform-api-url}")
    private String platformApiUrl;

    /** 锁定节点 ID（逗号分隔）：恒为顶层第一位 */
    @Value("${menu.locked-menu-ids:}")
    private String lockedMenuIdsRaw;

    /** 固定显示节点 ID（逗号分隔）：渲染时强制可见（不可隐藏） */
    @Value("${menu.no-hide-menu-ids:}")
    private String noHideMenuIdsRaw;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外入口 ====================

    /**
     * 主路径：转发平台菜单接口取基础树，叠加配置后返回。
     */
    public MenuTreeVO renderTree(HttpServletRequest request) {
        String sessionId = sessionContextService.extractSessionId(request);
        if (!StringUtils.hasText(sessionId)) {
            throw new UnauthorizedException("未登录：缺少会话 ID");
        }
        String body = forwardToPlatform(request, sessionId);
        List<RawNode> baseRoots = parsePlatformTree(body);
        return buildOverlayTree(baseRoots);
    }

    /**
     * 兜底路径：前端自传基础树（如主题壳缓存的上一版权限树），仅叠加配置。
     */
    public MenuTreeVO overlay(OverlayRequest request) {
        if (request == null || request.getMenus() == null || request.getMenus().isEmpty()) {
            throw new IllegalArgumentException("menus 不能为空");
        }
        return buildOverlayTree(convertOverlayTree(request.getMenus()));
    }

    // ==================== 平台接口转发 ====================

    private String forwardToPlatform(HttpServletRequest request, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        String cookie = request.getHeader("Cookie");
        if (StringUtils.hasText(cookie)) {
            headers.add("Cookie", cookie);
        }
        headers.set(SessionContextService.HEADER_SESSION_ID, sessionId);
        // 平台菜单接口以 Authorization 裸值（sessionId）鉴权（实测：Cookie / X-Session-Id / Bearer 均 401）
        headers.set(SessionContextService.HEADER_AUTHORIZATION, sessionId);
        try {
            ResponseEntity<String> resp = platformRestTemplate.exchange(
                    platformApiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = resp.getBody();
            log.info("平台菜单接口转发完成: url={}, status={}, bodyLength={}, bodyHead={}",
                    platformApiUrl, resp.getStatusCodeValue(),
                    body == null ? 0 : body.length(), head(body, 3000));
            if (!StringUtils.hasText(body)) {
                throw new PlatformApiException("平台菜单接口返回内容为空");
            }
            return body;
        } catch (RestClientException e) {
            log.error("平台菜单接口调用失败: url={}, sessionId={}", platformApiUrl, sessionId, e);
            throw new PlatformApiException("平台菜单接口调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 平台响应解析（结构兼容探测，联调期按日志修正） ====================

    private List<RawNode> parsePlatformTree(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new PlatformApiException("平台菜单接口响应不是合法 JSON: " + head(body, 500), e);
        }
        JsonNode arr = locateMenusArray(root);
        if (arr == null) {
            throw new PlatformApiException("平台菜单接口响应中未找到菜单数组: " + head(body, 500));
        }
        List<RawNode> nodes = new ArrayList<>();
        for (JsonNode item : arr) {
            RawNode n = convertJsonNode(item);
            if (n != null) {
                nodes.add(n);
            }
        }
        log.info("平台菜单响应解析完成: 顶层节点={}, 总节点={}", nodes.size(), countAll(nodes));
        return nodes;
    }

    /** 在常见包裹结构中定位菜单数组（根数组 / menus / data.menus / data / result.menus / result / list） */
    private JsonNode locateMenusArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        String[] paths = {"menus", "data.menus", "data", "result.menus", "result", "list"};
        for (String path : paths) {
            JsonNode cur = root;
            for (String seg : path.split("\\.")) {
                cur = cur.path(seg);
            }
            if (cur.isArray()) {
                return cur;
            }
        }
        return null;
    }

    private RawNode convertJsonNode(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String id = text(item, "id", "menuId", "menu_id", "menuID");
        if (!StringUtils.hasText(id)) {
            return null;
        }
        RawNode n = new RawNode();
        n.id = id;
        n.name = text(item, "name", "menuName", "title", "label");
        n.type = text(item, "type", "menuType", "nodeType");
        JsonNode children = firstArray(item, "children", "subMenus", "childList", "menus", "child");
        if (children != null) {
            for (JsonNode c : children) {
                RawNode rc = convertJsonNode(c);
                if (rc != null) {
                    if (n.children == null) {
                        n.children = new ArrayList<>();
                    }
                    n.children.add(rc);
                }
            }
        }
        return n;
    }

    private List<RawNode> convertOverlayTree(List<RenderNodeVO> menus) {
        List<RawNode> result = new ArrayList<>();
        for (RenderNodeVO vo : menus) {
            if (vo == null || !StringUtils.hasText(vo.getId())) {
                continue;
            }
            RawNode n = new RawNode();
            n.id = vo.getId();
            n.name = vo.getName();
            n.type = vo.getType();
            if (vo.getChildren() != null) {
                n.children = convertOverlayTree(vo.getChildren());
            }
            result.add(n);
        }
        return result;
    }

    // ==================== 叠加核心 ====================

    private MenuTreeVO buildOverlayTree(List<RawNode> baseRoots) {
        // 1) 扁平化基础树（保留原始父链与输入顺序）
        Map<String, RawNode> flat = new LinkedHashMap<>();
        Map<String, String> origParent = new HashMap<>();
        Map<String, Integer> origIndex = new HashMap<>();
        flatten(baseRoots, null, flat, origParent, origIndex);

        // 2) 配置与平台元数据
        Map<String, PlatformMenu> meta = MenuSupport.indexById(platformMenuMapper.selectAll());
        Map<String, MenuConfig> cfgMap = MenuSupport.loadConfigMap(menuConfigMapper);
        Set<String> locked = MenuSupport.parseIdSet(lockedMenuIdsRaw);
        Set<String> noHide = MenuSupport.parseIdSet(noHideMenuIdsRaw);

        // 3) 生效父分组
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (String id : flat.keySet()) {
            String p = effectiveParent(id, flat, origParent, cfgMap, locked);
            childrenOf.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
        }
        // 4) 排序：锁定最前 → 有 sort_no 段 → 无 sort_no 段按平台 show_order → 输入顺序
        Comparator<String> cmp = renderComparator(cfgMap, meta, origIndex, locked);
        for (List<String> seq : childrenOf.values()) {
            seq.sort(cmp);
        }

        // 5) 自顶层递归渲染（环路防护 + 不可达兜底）
        Set<String> visited = new HashSet<>();
        List<RenderNodeVO> menus = new ArrayList<>();
        for (String id : childrenOf.getOrDefault(MenuSupport.ROOT, Collections.emptyList())) {
            RenderNodeVO vo = renderNode(id, childrenOf, flat, meta, cfgMap, noHide, visited);
            if (vo != null) {
                menus.add(vo);
            }
        }
        for (String id : flat.keySet()) {
            if (!visited.contains(id)) {
                log.warn("菜单渲染存在不可达节点（可能存在配置环），已挂到顶层兜底: {}", id);
                RenderNodeVO vo = renderNode(id, childrenOf, flat, meta, cfgMap, noHide, visited);
                if (vo != null) {
                    menus.add(vo);
                }
            }
        }

        MenuTreeVO result = new MenuTreeVO();
        result.setMenus(menus);
        result.setVersion(MenuSupport.versionOf(cfgMap.values()));
        return result;
    }

    /**
     * 生效父：锁定节点强制顶层；配置改挂目标在基础集合内则用之，
     * 否则回退原始祖先链（防丢）；无配置则用基础树原父。
     */
    private String effectiveParent(String id, Map<String, RawNode> flat, Map<String, String> origParent,
                                   Map<String, MenuConfig> cfgMap, Set<String> locked) {
        if (locked.contains(id)) {
            return MenuSupport.ROOT;
        }
        MenuConfig cfg = cfgMap.get(id);
        if (cfg != null && StringUtils.hasText(cfg.getParentId())) {
            String target = cfg.getParentId();
            if (MenuSupport.ROOT.equals(target)) {
                return MenuSupport.ROOT;
            }
            if (flat.containsKey(target)) {
                return target;
            }
            return fallbackAncestor(id, flat, origParent);
        }
        String p = origParent.get(id);
        return StringUtils.hasText(p) ? p : MenuSupport.ROOT;
    }

    /** 沿原始祖先链上溯，返回第一个仍在基础集合内的祖先；无则顶层 */
    private String fallbackAncestor(String id, Map<String, RawNode> flat, Map<String, String> origParent) {
        String cur = origParent.get(id);
        int guard = 0;
        while (cur != null && guard++ < 1000) {
            if (flat.containsKey(cur)) {
                return cur;
            }
            cur = origParent.get(cur);
        }
        return MenuSupport.ROOT;
    }

    private Comparator<String> renderComparator(Map<String, MenuConfig> cfgMap, Map<String, PlatformMenu> meta,
                                                Map<String, Integer> origIndex, Set<String> locked) {
        return (a, b) -> {
            int c = Boolean.compare(locked.contains(b), locked.contains(a));
            if (c != 0) {
                return c;
            }
            MenuConfig ca = cfgMap.get(a);
            MenuConfig cb = cfgMap.get(b);
            Integer sa = ca == null ? null : ca.getSortNo();
            Integer sb = cb == null ? null : cb.getSortNo();
            c = Boolean.compare(sb != null, sa != null);
            if (c != 0) {
                return c;
            }
            if (sa != null && sb != null) {
                c = Integer.compare(sa, sb);
                if (c != 0) {
                    return c;
                }
            }
            PlatformMenu ma = meta.get(a);
            PlatformMenu mb = meta.get(b);
            c = MenuSupport.compareDoubleNullLast(ma == null ? null : ma.getShowOrder(),
                    mb == null ? null : mb.getShowOrder());
            if (c != 0) {
                return c;
            }
            Integer ia = origIndex.get(a);
            Integer ib = origIndex.get(b);
            if (ia != null && ib != null) {
                c = Integer.compare(ia, ib);
                if (c != 0) {
                    return c;
                }
            }
            return a.compareTo(b);
        };
    }

    private RenderNodeVO renderNode(String id, Map<String, List<String>> childrenOf, Map<String, RawNode> flat,
                                    Map<String, PlatformMenu> meta, Map<String, MenuConfig> cfgMap,
                                    Set<String> noHide, Set<String> visited) {
        if (!visited.add(id)) {
            return null;
        }
        RawNode raw = flat.get(id);
        PlatformMenu m = meta.get(id);
        MenuConfig cfg = cfgMap.get(id);

        RenderNodeVO vo = new RenderNodeVO();
        vo.setId(id);
        // 名称：配置改名 > 平台名 > 接口名
        String name;
        if (cfg != null && StringUtils.hasText(cfg.getDisplayName())) {
            name = cfg.getDisplayName();
        } else if (m != null && StringUtils.hasText(m.getName())) {
            name = m.getName();
        } else {
            name = raw != null && StringUtils.hasText(raw.name) ? raw.name : id;
        }
        vo.setName(name);
        // 类型：平台库权威，接口值兜底归一
        vo.setType(m != null ? m.getDisplayType() : normalizeType(raw == null ? null : raw.type));
        // 显隐：固定显示节点强制可见；否则无配置默认显示
        vo.setVisible(noHide.contains(id) || cfg == null || cfg.getVisible() == null || cfg.getVisible() != 0);

        List<String> childIds = childrenOf.get(id);
        if (childIds != null && !childIds.isEmpty()) {
            List<RenderNodeVO> children = new ArrayList<>();
            for (String cid : childIds) {
                RenderNodeVO child = renderNode(cid, childrenOf, flat, meta, cfgMap, noHide, visited);
                if (child != null) {
                    children.add(child);
                }
            }
            if (!children.isEmpty()) {
                vo.setChildren(children);
            }
        }
        return vo;
    }

    // ==================== 内部工具 ====================

    private void flatten(List<RawNode> nodes, String parentId, Map<String, RawNode> flat,
                         Map<String, String> origParent, Map<String, Integer> origIndex) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (RawNode n : nodes) {
            if (!StringUtils.hasText(n.id)) {
                continue;
            }
            if (flat.containsKey(n.id)) {
                log.warn("基础菜单树存在重复节点 id，忽略后出现的节点: {}", n.id);
                continue;
            }
            flat.put(n.id, n);
            if (parentId != null) {
                origParent.put(n.id, parentId);
            }
            origIndex.put(n.id, origIndex.size());
            flatten(n.children, n.id, flat, origParent, origIndex);
        }
    }

    private static String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && v.isValueNode() && StringUtils.hasText(v.asText())) {
                return v.asText().trim();
            }
        }
        return null;
    }

    private static JsonNode firstArray(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isArray()) {
                return v;
            }
        }
        return null;
    }

    private static int countAll(List<RawNode> nodes) {
        if (nodes == null) {
            return 0;
        }
        int count = 0;
        for (RawNode n : nodes) {
            count += 1 + countAll(n.children);
        }
        return count;
    }

    private static String normalizeType(String t) {
        if (!StringUtils.hasText(t)) {
            return "MENU";
        }
        String u = t.trim().toUpperCase();
        return "GROUP".equals(u) || "#GROUP#".equals(u) ? "GROUP" : "MENU";
    }

    private static String head(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ");
        return one.length() <= max ? one : one.substring(0, max) + "...(截断)";
    }

    /** 基础树节点（接口结构兼容层的中间表示） */
    private static class RawNode {
        String id;
        String name;
        String type;
        List<RawNode> children;
    }
}
