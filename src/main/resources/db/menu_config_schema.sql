-- ============================================================
-- 菜单展示配置 自建表（库 qixiao-apaas / 人大金仓 KingbaseES v8）
-- 旁路配置：不修改平台菜单库 t_apaas_sys_menu，仅在菜单渲染时叠加
-- corp_code='hltgq' 的展示规则（改名 / 改挂 / 排序 / 显隐）
-- 稀疏存储：一行 = 一个菜单与平台现状的差异；无差异的菜单不落行
-- 执行：ksql -p 54321 -U system -d qixiao-apaas -f menu_config_schema.sql
-- ============================================================

-- 1) 菜单展示配置表
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_menu_config (
    id           VARCHAR(64) PRIMARY KEY,
    corp_code    VARCHAR(64) NOT NULL DEFAULT 'hltgq',
    menu_id      VARCHAR(64) NOT NULL,
    parent_id    VARCHAR(64),
    sort_no      INTEGER,
    display_name VARCHAR(255),
    visible      SMALLINT    NOT NULL DEFAULT 1,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64)
);

-- 每个菜单一行配置
CREATE UNIQUE INDEX IF NOT EXISTS uk_hltgq_menu_config_menu ON "qixiao-apaas".t_auto_hltgq_menu_config(menu_id);
