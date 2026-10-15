-- ============================================================
-- 菜单展示配置 初始化（目的态基线：顶层顺序 + 隐藏项）
-- 前置：先执行 menu_config_schema.sql
-- 执行：ksql -p 54321 -U system -d qixiao-apaas -f menu_config_init.sql
-- 幂等：按 menu_id 冲突则更新（重跑 = 重置为初始状态；管理员页面改过的
--       内容会被覆盖回基线）
-- 说明：id 统一用 'mc_' || menu_id 派生（本表自用行，无外部引用）
-- ============================================================

INSERT INTO "qixiao-apaas".t_auto_hltgq_menu_config
    (id, corp_code, menu_id, parent_id, sort_no, display_name, visible, created_at, updated_at, updated_by)
VALUES
    -- 隐藏：业务中心壳（各应用子节点提级到顶层展示，界面不再显示该分组）
    ('mc_0bdbb2c325525e986d55ef50ca66d056', 'hltgq', '0bdbb2c325525e986d55ef50ca66d056', NULL, NULL, NULL, 0, now(), now(), 'init'),
    -- 顶层提级 + 排序：灌区一张图① 运行监测② 水资源调度③ 应急管理④ 智能决策⑤ 系统中心⑥
    ('mc_Yg0tLCC0v0IUKnS9VEPe', 'hltgq', 'Yg0tLCC0v0IUKnS9VEPe', '#ROOT#', 1, NULL, 1, now(), now(), 'init'),
    ('mc_78cd1df350c7767c90b7bdecf0d8f16b', 'hltgq', '78cd1df350c7767c90b7bdecf0d8f16b', '#ROOT#', 2, NULL, 1, now(), now(), 'init'),
    ('mc_8323e9b4e6a1c62ff236135adf1cb57c', 'hltgq', '8323e9b4e6a1c62ff236135adf1cb57c', '#ROOT#', 3, NULL, 1, now(), now(), 'init'),
    ('mc_c601966bbca7c44bb1f555e46bed2b33', 'hltgq', 'c601966bbca7c44bb1f555e46bed2b33', '#ROOT#', 4, NULL, 1, now(), now(), 'init'),
    ('mc_75f7e7b377173dea9163b74bcca21b9c', 'hltgq', '75f7e7b377173dea9163b74bcca21b9c', '#ROOT#', 5, NULL, 1, now(), now(), 'init'),
    ('mc_fa177138f94a7ea01f549b1aa7893d03', 'hltgq', 'fa177138f94a7ea01f549b1aa7893d03', '#ROOT#', 6, NULL, 1, now(), now(), 'init'),
    -- 隐藏：遗留节点（不在目的态清单中）
    ('mc_3d22880f5e4940a54003934f959f987e', 'hltgq', '3d22880f5e4940a54003934f959f987e', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_nc2gorWvJM8mflwoA1P', 'hltgq', 'nc2gorWvJM8mflwoA1P', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_nGc6NconcM4dlT0HXnK', 'hltgq', 'nGc6NconcM4dlT0HXnK', NULL, NULL, NULL, 0, now(), now(), 'init')
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id    = EXCLUDED.parent_id,
    sort_no      = EXCLUDED.sort_no,
    display_name = EXCLUDED.display_name,
    visible      = EXCLUDED.visible,
    updated_at   = now(),
    updated_by   = 'init';
