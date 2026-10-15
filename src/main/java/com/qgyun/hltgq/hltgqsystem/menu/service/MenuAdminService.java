package com.qgyun.hltgq.hltgqsystem.menu.service;

import com.qgyun.hltgq.hltgqsystem.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsystem.auth.UserContext;
import com.qgyun.hltgq.hltgqsystem.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsystem.menu.entity.MenuConfig;
import com.qgyun.hltgq.hltgqsystem.menu.entity.PlatformMenu;
import com.qgyun.hltgq.hltgqsystem.menu.mapper.MenuConfigMapper;
import com.qgyun.hltgq.hltgqsystem.menu.mapper.PlatformMenuMapper;
import com.qgyun.hltgq.hltgqsystem.menu.vo.AdminTreeNodeVO;
import com.qgyun.hltgq.hltgqsystem.menu.vo.AdminTreeVO;
import com.qgyun.hltgq.hltgqsystem.menu.vo.MenuSaveRequest;
import com.qgyun.hltgq.hltgqsystem.menu.vo.SaveNode;
import com.qgyun.hltgq.hltgqsystem.menu.vo.SaveResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 菜单管理服务（管理端）：生效结构查询、整树保存、还原默认。
 * <p>生效结构 = 平台全量菜单（t_apaas_sys_menu, corp_code='hltgq'）叠加旁路配置
 * （t_auto_hltgq_menu_config）后的视图；管理端展示全部节点（含隐藏/孤儿节点）。
 * <p>保存采用"整树提交 + 后端 diff"：
 * <ul>
 *   <li>提交数组顺序 = 展示顺序；每项为 {menuId, parentId, displayName, visible}</li>
 *   <li>与平台现状一致的项不落配置（稀疏存储）；差异项重算配置行</li>
 *   <li>某父下顺序与生效态不同时：与平台默认顺序一致 → 清除排序配置；否则该父全部节点重写 sort_no 1..n</li>
 *   <li>锁定节点（如灌区一张图）：强制顶层、恒第一位、不可改名/隐藏，提交中的变更值一律忽略</li>
 *   <li>固定显示节点（如菜单管理入口）：不可隐藏，提交中的隐藏值一律忽略</li>
 *   <li>管理页排除节点（menu.admin-excluded-ids）：管理树不展示其整棵子树，配置行保留不动</li>
 *   <li>未提交的节点：保留其现有配置行不动</li>
 * </ul>
 */
@Service
public class MenuAdminService {

    private static final Logger log = LoggerFactory.getLogger(MenuAdminService.class);

    /** 排序处理模式：不变 / 清排序（回到平台默认序）/ 重写排序 */
    private static final String MODE_NONE = "NONE";
    private static final String MODE_CLEAR = "CLEAR";
    private static final String MODE_REWRITE = "REWRITE";

    /** 出厂基线：顶层提级排序（按数组序 1..n） */
    private static final String[] BASELINE_TOP_ORDER = {
            "Yg0tLCC0v0IUKnS9VEPe", // 灌区一张图（锁定，恒第一位）
            "78cd1df350c7767c90b7bdecf0d8f16b", // 运行监测
            "8323e9b4e6a1c62ff236135adf1cb57c", // 水资源调度
            "c601966bbca7c44bb1f555e46bed2b33", // 应急管理
            "75f7e7b377173dea9163b74bcca21b9c", // 智能决策
            "fa177138f94a7ea01f549b1aa7893d03", // 系统中心
    };

    /** 出厂基线：隐藏节点（分组壳及其下全部子菜单，逐节点显式隐藏） */
    private static final String[] BASELINE_HIDDEN_IDS = {
            "0bdbb2c325525e986d55ef50ca66d056", // 业务中心（分组壳）
            "3d22880f5e4940a54003934f959f987e", // 灌区综合管理系统（旧分组）
            "4emTFV1oazxEmgsmKrd", // 站点管理
            "QyXChZiQWnct09LNOoC", // 站点层级
            "ShvaIpfCTEc2VfqLJea", // 图例模型
            "TAM8VECmAPuiWC9h8UG", // 站点排序表
            "nc2gorWvJM8mflwoA1P", // 授权管理
            "nGc6NconcM4dlT0HXnK", // 模板管理（设计器遗留）
            "xKEt1IrBuPhwlMhnxff", // 功能管理（分组）
            "rMB3pXbox0EmIbdn2t0", // 应用管理
            "BmCUv5xXteHMXMoFQ0J", // 插件管理
            "oETNvcmuLxloNmb1iqe", // 任务监控
            "QS0xMAjkR4jyAtefSYk", // 接口中心（分组）
    };

    @Autowired
    private PlatformMenuMapper platformMenuMapper;

    @Autowired
    private MenuConfigMapper menuConfigMapper;

    @Autowired
    private SessionContextService sessionContextService;

    /** 锁定节点 ID（逗号分隔）：管理端禁改、展示端恒为顶层第一位 */
    @Value("${menu.locked-menu-ids:}")
    private String lockedMenuIdsRaw;

    /** 固定显示节点 ID（逗号分隔）：不允许被隐藏（保存时强制可见） */
    @Value("${menu.no-hide-menu-ids:}")
    private String noHideMenuIdsRaw;

    /** 管理页排除节点 ID（逗号分隔）：管理树中不展示这些节点及其整棵子树 */
    @Value("${menu.admin-excluded-ids:}")
    private String adminExcludedIdsRaw;

    // ==================== 生效结构查询 ====================

    /**
     * 管理端整树（全量节点 + 生效结构 + 锁定/孤儿标记）。
     */
    public AdminTreeVO getAdminTree() {
        List<PlatformMenu> all = platformMenuMapper.selectAll();
        Map<String, PlatformMenu> nodeMap = MenuSupport.indexById(all);
        Map<String, MenuConfig> cfgMap = MenuSupport.loadConfigMap(menuConfigMapper);
        Set<String> locked = MenuSupport.parseIdSet(lockedMenuIdsRaw);
        Set<String> noHide = MenuSupport.parseIdSet(noHideMenuIdsRaw);
        Set<String> excludedRoots = MenuSupport.parseIdSet(adminExcludedIdsRaw);

        Map<String, List<String>> seq = buildEffectiveSequence(all, nodeMap, cfgMap, locked);

        // 管理页排除：沿生效结构收集"根 + 全部后代"，从各序列移除并预置已访问（防兜底重挂）
        Set<String> excluded = new HashSet<>();
        for (String rootId : excludedRoots) {
            collectBranch(rootId, seq, excluded);
        }
        for (List<String> list : seq.values()) {
            list.removeAll(excluded);
        }

        Set<String> visited = new HashSet<>(excluded);
        List<AdminTreeNodeVO> tree = new ArrayList<>();
        for (String id : seq.getOrDefault(MenuSupport.ROOT, Collections.emptyList())) {
            AdminTreeNodeVO node = buildAdminNode(id, nodeMap, cfgMap, seq, locked, noHide, visited);
            if (node != null) {
                tree.add(node);
            }
        }
        // 兜底：配置环路等异常导致不可达的节点挂到顶层（避免管理页丢失节点）
        for (PlatformMenu n : all) {
            if (!visited.contains(n.getId())) {
                log.warn("菜单结构存在不可达节点（可能存在配置环），已挂到顶层兜底展示: {}", n.getId());
                AdminTreeNodeVO node = buildAdminNode(n.getId(), nodeMap, cfgMap, seq, locked, noHide, visited);
                if (node != null) {
                    tree.add(node);
                }
            }
        }

        AdminTreeVO vo = new AdminTreeVO();
        vo.setVersion(MenuSupport.versionOf(cfgMap.values()));
        vo.setLockedMenuIds(new ArrayList<>(locked));
        vo.setTree(tree);
        return vo;
    }

    // ==================== 整树保存 ====================

    /**
     * 整树保存：按提交的树结构与平台现状 diff，只改写有差异的配置行。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveResultVO save(MenuSaveRequest request) {
        if (request == null || request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new IllegalArgumentException("nodes 不能为空");
        }
        List<SaveNode> nodes = request.getNodes();

        List<PlatformMenu> all = platformMenuMapper.selectAll();
        Map<String, PlatformMenu> nodeMap = MenuSupport.indexById(all);
        Map<String, MenuConfig> cfgMap = MenuSupport.loadConfigMap(menuConfigMapper);
        Set<String> locked = MenuSupport.parseIdSet(lockedMenuIdsRaw);
        Set<String> noHide = MenuSupport.parseIdSet(noHideMenuIdsRaw);

        // ---------- 1. 校验 ----------
        Set<String> submitIds = new LinkedHashSet<>();
        for (SaveNode s : nodes) {
            if (!StringUtils.hasText(s.getMenuId())) {
                throw new IllegalArgumentException("存在 menuId 为空的节点");
            }
            if (!submitIds.add(s.getMenuId())) {
                throw new IllegalArgumentException("提交的 menuId 重复: " + s.getMenuId());
            }
        }
        Map<String, String> submitParent = new LinkedHashMap<>();
        for (SaveNode s : nodes) {
            if (!nodeMap.containsKey(s.getMenuId())) {
                throw new IllegalArgumentException("menuId 不在平台菜单库: " + s.getMenuId());
            }
            String p = normalizeParent(s.getParentId());
            if (!MenuSupport.ROOT.equals(p) && !nodeMap.containsKey(p)) {
                throw new IllegalArgumentException("parentId 不在平台菜单库: " + p);
            }
            submitParent.put(s.getMenuId(), p);
        }
        // 防环：沿提交的父子链上溯，出现自身即拒绝
        for (String id : submitIds) {
            String cur = id;
            int guard = 0;
            while (true) {
                String p = submitParent.get(cur);
                if (p == null || MenuSupport.ROOT.equals(p) || !submitIds.contains(p)) {
                    break;
                }
                if (p.equals(id)) {
                    throw new IllegalArgumentException("菜单挂载存在循环: " + id);
                }
                if (++guard > submitIds.size() + 1) {
                    throw new IllegalArgumentException("菜单挂载层级异常: " + id);
                }
                cur = p;
            }
        }

        // ---------- 2. 提交态序列（含锁定节点提前到最前） ----------
        Map<String, List<String>> submitSeq = new LinkedHashMap<>();
        for (SaveNode s : nodes) {
            submitSeq.computeIfAbsent(submitParent.get(s.getMenuId()), k -> new ArrayList<>()).add(s.getMenuId());
        }
        for (List<String> seq : submitSeq.values()) {
            MenuSupport.promoteLocked(seq, locked);
        }
        Map<String, Integer> submitIndex = new HashMap<>();
        for (List<String> seq : submitSeq.values()) {
            for (int i = 0; i < seq.size(); i++) {
                submitIndex.put(seq.get(i), i);
            }
        }

        // ---------- 3. 当前生效态序列 ----------
        Map<String, List<String>> effSeq = buildEffectiveSequence(all, nodeMap, cfgMap, locked);

        // ---------- 4. 每个父的排序处理模式 ----------
        Map<String, String> parentMode = new HashMap<>();
        Set<String> allParents = new HashSet<>();
        allParents.addAll(submitSeq.keySet());
        allParents.addAll(effSeq.keySet());
        for (String p : allParents) {
            List<String> s = submitSeq.getOrDefault(p, Collections.emptyList());
            List<String> e = effSeq.getOrDefault(p, Collections.emptyList());
            if (s.equals(e)) {
                parentMode.put(p, MODE_NONE);
            } else if (isDefaultOrder(p, s, all, nodeMap)) {
                parentMode.put(p, MODE_CLEAR);
            } else {
                parentMode.put(p, MODE_REWRITE);
            }
        }

        // ---------- 5. 计算目标配置行 ----------
        Map<String, MenuConfig> target = new HashMap<>();
        for (SaveNode s : nodes) {
            String id = s.getMenuId();
            PlatformMenu n = nodeMap.get(id);
            boolean isLocked = locked.contains(id);
            String submitP = submitParent.get(id);

            // 挂载差异：基准为平台原父（顶层/孤儿统一按 ROOT 归一）
            String parentCfg;
            if (isLocked) {
                parentCfg = MenuSupport.ROOT;
            } else {
                parentCfg = submitP.equals(platformParentNormalized(n, nodeMap)) ? null : submitP;
            }

            // 改名差异：与平台原名一致则不落配置
            String submitName = StringUtils.hasText(s.getDisplayName()) ? s.getDisplayName().trim() : n.getName();
            String nameCfg = (!isLocked && !submitName.equals(n.getName())) ? submitName : null;

            // 显隐：锁定 / 固定显示节点强制可见
            int visibleVal = (isLocked || noHide.contains(id)) ? 1 : (Boolean.FALSE.equals(s.getVisible()) ? 0 : 1);

            // 排序
            String mode = parentMode.getOrDefault(submitP, MODE_NONE);
            Integer sortCfg;
            if (MODE_REWRITE.equals(mode)) {
                Integer idx = submitIndex.get(id);
                sortCfg = idx != null ? idx + 1 : null;
            } else if (MODE_CLEAR.equals(mode)) {
                sortCfg = null;
            } else {
                MenuConfig cur = cfgMap.get(id);
                sortCfg = cur != null ? cur.getSortNo() : null;
            }

            boolean rowExists = parentCfg != null || nameCfg != null || visibleVal == 0 || sortCfg != null;
            if (!rowExists) {
                continue;
            }
            MenuConfig row = new MenuConfig();
            row.setMenuId(id);
            row.setParentId(parentCfg);
            row.setDisplayName(nameCfg);
            row.setVisible(visibleVal);
            row.setSortNo(sortCfg);
            target.put(id, row);
        }

        // ---------- 6. 写库 diff（保留 created_at，仅变更行更新） ----------
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        int changed = 0;

        // 删除：已提交且目标不再产生配置的既有行（含"恢复默认"被清掉的行）
        for (MenuConfig cur : cfgMap.values()) {
            if (submitIds.contains(cur.getMenuId()) && !target.containsKey(cur.getMenuId())) {
                menuConfigMapper.deleteById(cur.getId());
                changed++;
            }
        }
        // 新增 / 更新
        for (MenuConfig row : target.values()) {
            MenuConfig cur = cfgMap.get(row.getMenuId());
            if (cur == null) {
                row.setCorpCode("hltgq");
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                row.setUpdatedBy(operator);
                menuConfigMapper.insert(row);
                changed++;
            } else if (differs(cur, row)) {
                cur.setParentId(row.getParentId());
                cur.setDisplayName(row.getDisplayName());
                cur.setVisible(row.getVisible());
                cur.setSortNo(row.getSortNo());
                cur.setUpdatedAt(now);
                cur.setUpdatedBy(operator);
                menuConfigMapper.updateById(cur);
                changed++;
            }
        }

        log.info("菜单配置保存完成: 提交节点={}, 变更行={}, 操作人={}", nodes.size(), changed, operator);
        SaveResultVO result = new SaveResultVO();
        result.setTotal(nodes.size());
        result.setChanged(changed);
        return result;
    }

    /**
     * 还原默认：恢复出厂基线——清空全部配置行后按内置基线重建
     * （顶层提级排序 + 隐藏项，与 db/menu_config_init.sql、menu_config_hide_extra.sql 一致）。
     *
     * @return 重建的配置行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int reset() {
        Map<String, MenuConfig> cfgMap = MenuSupport.loadConfigMap(menuConfigMapper);
        int removed = 0;
        for (MenuConfig cur : cfgMap.values()) {
            menuConfigMapper.deleteById(cur.getId());
            removed++;
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        if (!StringUtils.hasText(operator)) {
            operator = "reset";
        }
        int rebuilt = 0;
        for (int i = 0; i < BASELINE_TOP_ORDER.length; i++) {
            insertBaselineRow(BASELINE_TOP_ORDER[i], MenuSupport.ROOT, i + 1, 1, now, operator);
            rebuilt++;
        }
        for (String id : BASELINE_HIDDEN_IDS) {
            insertBaselineRow(id, null, null, 0, now, operator);
            rebuilt++;
        }
        log.info("菜单配置已还原默认（重建出厂基线）: 删除行={}, 重建行={}, 操作人={}", removed, rebuilt, operator);
        return rebuilt;
    }

    /** 写出厂基线行 */
    private void insertBaselineRow(String menuId, String parentId, Integer sortNo, int visible,
                                   LocalDateTime now, String operator) {
        MenuConfig row = new MenuConfig();
        row.setCorpCode("hltgq");
        row.setMenuId(menuId);
        row.setParentId(parentId);
        row.setSortNo(sortNo);
        row.setVisible(visible);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setUpdatedBy(operator);
        menuConfigMapper.insert(row);
    }

    // ==================== 内部：生效结构计算 ====================

    /**
     * 计算每个父节点下的"生效序列"（id 列表，已排序、锁定节点已提前）。
     * <p>生效父 = 配置改挂（有效目标）> 平台原父；目标/原父不在集合或锁定节点 → 顶层。
     */
    private Map<String, List<String>> buildEffectiveSequence(List<PlatformMenu> all,
                                                             Map<String, PlatformMenu> nodeMap,
                                                             Map<String, MenuConfig> cfgMap,
                                                             Set<String> locked) {
        Map<String, List<PlatformMenu>> byParent = new HashMap<>();
        for (PlatformMenu n : all) {
            String p = effectiveParentOf(n, nodeMap, cfgMap, locked);
            byParent.computeIfAbsent(p, k -> new ArrayList<>()).add(n);
        }
        Map<String, List<String>> seq = new LinkedHashMap<>();
        for (Map.Entry<String, List<PlatformMenu>> e : byParent.entrySet()) {
            List<PlatformMenu> list = e.getValue();
            list.sort(effectiveComparator(cfgMap, locked));
            List<String> ids = new ArrayList<>(list.size());
            for (PlatformMenu n : list) {
                ids.add(n.getId());
            }
            seq.put(e.getKey(), ids);
        }
        return seq;
    }

    /**
     * 生效父：锁定节点强制顶层；配置改挂有效则用之；否则平台原父（不在集合时按顶层归一）。
     */
    private String effectiveParentOf(PlatformMenu n, Map<String, PlatformMenu> nodeMap,
                                     Map<String, MenuConfig> cfgMap, Set<String> locked) {
        if (locked.contains(n.getId())) {
            return MenuSupport.ROOT;
        }
        MenuConfig cfg = cfgMap.get(n.getId());
        if (cfg != null && StringUtils.hasText(cfg.getParentId())) {
            String target = cfg.getParentId();
            if (MenuSupport.ROOT.equals(target) || nodeMap.containsKey(target)) {
                return target;
            }
            return MenuSupport.ROOT;
        }
        String p = n.getAppParent();
        return (StringUtils.hasText(p) && nodeMap.containsKey(p)) ? p : MenuSupport.ROOT;
    }

    /**
     * 平台原父归一（用于保存 diff 基准）：顶层及"父不在集合"的孤儿统一按顶层处理。
     */
    private String platformParentNormalized(PlatformMenu n, Map<String, PlatformMenu> nodeMap) {
        String p = n.getAppParent();
        if (!StringUtils.hasText(p) || !nodeMap.containsKey(p)) {
            return MenuSupport.ROOT;
        }
        return p;
    }

    /**
     * 生效排序比较器：锁定最前 → 有 sort_no 的段（按 sort_no）→ 无 sort_no 段（按平台 show_order）→ id。
     */
    private Comparator<PlatformMenu> effectiveComparator(Map<String, MenuConfig> cfgMap, Set<String> locked) {
        return (a, b) -> {
            int c = Boolean.compare(locked.contains(b.getId()), locked.contains(a.getId()));
            if (c != 0) {
                return c;
            }
            MenuConfig ca = cfgMap.get(a.getId());
            MenuConfig cb = cfgMap.get(b.getId());
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
            c = MenuSupport.compareDoubleNullLast(a.getShowOrder(), b.getShowOrder());
            if (c != 0) {
                return c;
            }
            return String.valueOf(a.getId()).compareTo(String.valueOf(b.getId()));
        };
    }

    /**
     * 判定提交序是否恰为该父的平台默认序：
     * 提交成员全部为该父的平台原生成员，且顺序与"原生成员按 show_order"完全一致。
     * 满足则可安全清除该父的排序配置（回到跟随平台）。
     */
    private boolean isDefaultOrder(String parent, List<String> submitSeqOfParent,
                                   List<PlatformMenu> all, Map<String, PlatformMenu> nodeMap) {
        for (String id : submitSeqOfParent) {
            PlatformMenu n = nodeMap.get(id);
            if (n == null || !platformParentNormalized(n, nodeMap).equals(parent)) {
                return false;
            }
        }
        List<PlatformMenu> natives = new ArrayList<>();
        for (PlatformMenu n : all) {
            if (platformParentNormalized(n, nodeMap).equals(parent)) {
                natives.add(n);
            }
        }
        natives.sort((a, b) -> {
            int c = MenuSupport.compareDoubleNullLast(a.getShowOrder(), b.getShowOrder());
            if (c != 0) {
                return c;
            }
            return String.valueOf(a.getId()).compareTo(String.valueOf(b.getId()));
        });
        List<String> expected = new ArrayList<>();
        for (PlatformMenu n : natives) {
            if (submitSeqOfParent.contains(n.getId())) {
                expected.add(n.getId());
            }
        }
        return expected.equals(submitSeqOfParent);
    }

    /** 沿生效序列收集某节点分支的全部 id（含自身；环路防护） */
    private void collectBranch(String id, Map<String, List<String>> seq, Set<String> out) {
        if (!out.add(id)) {
            return;
        }
        List<String> children = seq.get(id);
        if (children != null) {
            for (String child : children) {
                collectBranch(child, seq, out);
            }
        }
    }

    /**
     * 构建管理树节点（递归，含环路防护）。
     */
    private AdminTreeNodeVO buildAdminNode(String id, Map<String, PlatformMenu> nodeMap,
                                           Map<String, MenuConfig> cfgMap, Map<String, List<String>> seq,
                                           Set<String> locked, Set<String> noHide, Set<String> visited) {
        if (!visited.add(id)) {
            return null;
        }
        PlatformMenu n = nodeMap.get(id);
        if (n == null) {
            return null;
        }
        MenuConfig cfg = cfgMap.get(id);
        AdminTreeNodeVO vo = new AdminTreeNodeVO();
        vo.setMenuId(id);
        vo.setName(cfg != null && StringUtils.hasText(cfg.getDisplayName()) ? cfg.getDisplayName() : n.getName());
        vo.setOriginalName(n.getName());
        vo.setType(n.getDisplayType());
        vo.setVisible(cfg == null || cfg.getVisible() == null || cfg.getVisible() != 0);
        vo.setLocked(locked.contains(id));
        vo.setNoHide(noHide.contains(id));
        vo.setOrphan(StringUtils.hasText(n.getAppParent()) && !nodeMap.containsKey(n.getAppParent()));

        List<String> childIds = seq.get(id);
        if (childIds != null && !childIds.isEmpty()) {
            List<AdminTreeNodeVO> children = new ArrayList<>();
            for (String cid : childIds) {
                AdminTreeNodeVO child = buildAdminNode(cid, nodeMap, cfgMap, seq, locked, noHide, visited);
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

    // ==================== 内部：通用 ====================

    private static boolean differs(MenuConfig a, MenuConfig b) {
        return !Objects.equals(a.getParentId(), b.getParentId())
                || !Objects.equals(a.getDisplayName(), b.getDisplayName())
                || !Objects.equals(normalizeVisible(a.getVisible()), b.getVisible())
                || !Objects.equals(a.getSortNo(), b.getSortNo());
    }

    private static Integer normalizeVisible(Integer visible) {
        return visible == null ? Integer.valueOf(1) : visible;
    }

    private static String normalizeParent(String parentId) {
        return StringUtils.hasText(parentId) ? parentId.trim() : MenuSupport.ROOT;
    }

    /** 当前操作人（登录名）；获取失败返回 null（配置仍可保存） */
    private String currentOperator() {
        try {
            UserContext user = UserContextHolder.currentUser();
            if (user != null) {
                return sessionContextService.resolveLoginName(user);
            }
        } catch (Exception e) {
            log.warn("获取当前操作人失败: {}", e.getMessage());
        }
        return null;
    }
}
