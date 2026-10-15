-- ============================================================
-- 操作日志监控 自建表（库 qixiao-apaas / 人大金仓 KingbaseES v8）
-- 执行：ksql -p 54321 -U system -d qixiao-apaas -f oplog_schema.sql
-- ============================================================

-- 1) 操作日志主表
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_op_log (
    id            VARCHAR(64) PRIMARY KEY,
    corp_code     VARCHAR(64),
    group_name    VARCHAR(128),
    table_name    VARCHAR(128) NOT NULL,
    form_name     VARCHAR(255),
    biz_id        VARCHAR(64),
    op_type       VARCHAR(16)  NOT NULL,
    op_at         TIMESTAMP,
    operator_id   VARCHAR(64),
    operator_name VARCHAR(128),
    change_fields TEXT,
    before_json   TEXT,
    after_json    TEXT,
    created_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_op_log_op_at     ON "qixiao-apaas".t_auto_hltgq_op_log(op_at DESC);
CREATE INDEX IF NOT EXISTS idx_op_log_table     ON "qixiao-apaas".t_auto_hltgq_op_log(table_name);
CREATE INDEX IF NOT EXISTS idx_op_log_operator  ON "qixiao-apaas".t_auto_hltgq_op_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_op_log_group     ON "qixiao-apaas".t_auto_hltgq_op_log(group_name);

-- 2) 扫描水位线（每张被监控表一行）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_op_log_state (
    table_name   VARCHAR(128) PRIMARY KEY,
    watermark    VARCHAR(32),
    last_scan_at TIMESTAMP
);

-- 3) 行快照（删除识别 + UPDATE 的 before 快照）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_op_log_snapshot (
    id         VARCHAR(64) PRIMARY KEY,
    table_name VARCHAR(128) NOT NULL,
    biz_id     VARCHAR(64)  NOT NULL,
    row_json   TEXT,
    seen_at    TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_op_log_snapshot ON "qixiao-apaas".t_auto_hltgq_op_log_snapshot(table_name, biz_id);
CREATE INDEX IF NOT EXISTS idx_op_log_snapshot_table ON "qixiao-apaas".t_auto_hltgq_op_log_snapshot(table_name);
