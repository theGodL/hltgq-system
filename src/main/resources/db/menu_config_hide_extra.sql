-- ============================================================
-- 菜单展示配置 补充隐藏（第二批：业务中心组剩余子项、功能管理组、
-- 任务监控、接口中心组、遗留模板管理）
-- 前置：已执行 menu_config_schema.sql（建表）与 menu_config_init.sql
-- 执行：ksql -p 54321 -U system -d qixiao-apaas -f menu_config_hide_extra.sql
-- 幂等：按 menu_id 冲突则更新（重跑 = 重置这些节点为隐藏状态）
-- 说明：id 统一用 'mc_' || menu_id 派生（本表自用行，无外部引用）
-- ============================================================

INSERT INTO "qixiao-apaas".t_auto_hltgq_menu_config
    (id, corp_code, menu_id, parent_id, sort_no, display_name, visible, created_at, updated_at, updated_by)
VALUES
    -- 隐藏：业务中心壳下遗留组"灌区综合管理系统"及其全部子菜单
    ('mc_4emTFV1oazxEmgsmKrd', 'hltgq', '4emTFV1oazxEmgsmKrd', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_QyXChZiQWnct09LNOoC', 'hltgq', 'QyXChZiQWnct09LNOoC', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_ShvaIpfCTEc2VfqLJea', 'hltgq', 'ShvaIpfCTEc2VfqLJea', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_TAM8VECmAPuiWC9h8UG', 'hltgq', 'TAM8VECmAPuiWC9h8UG', NULL, NULL, NULL, 0, now(), now(), 'init'),
    -- 隐藏：功能管理组及其全部子菜单
    ('mc_xKEt1IrBuPhwlMhnxff', 'hltgq', 'xKEt1IrBuPhwlMhnxff', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_rMB3pXbox0EmIbdn2t0', 'hltgq', 'rMB3pXbox0EmIbdn2t0', NULL, NULL, NULL, 0, now(), now(), 'init'),
    ('mc_BmCUv5xXteHMXMoFQ0J', 'hltgq', 'BmCUv5xXteHMXMoFQ0J', NULL, NULL, NULL, 0, now(), now(), 'init'),
    -- 隐藏：任务监控
    ('mc_oETNvcmuLxloNmb1iqe', 'hltgq', 'oETNvcmuLxloNmb1iqe', NULL, NULL, NULL, 0, now(), now(), 'init'),
    -- 隐藏：接口中心组（其子"授权管理"已在初始化中隐藏）
    ('mc_QS0xMAjkR4jyAtefSYk', 'hltgq', 'QS0xMAjkR4jyAtefSYk', NULL, NULL, NULL, 0, now(), now(), 'init')
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id    = EXCLUDED.parent_id,
    sort_no      = EXCLUDED.sort_no,
    display_name = EXCLUDED.display_name,
    visible      = EXCLUDED.visible,
    updated_at   = now(),
    updated_by   = 'init';
