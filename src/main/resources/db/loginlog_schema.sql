-- ============================================================
-- 登录日志（自建）  库 qixiao-apaas / 人大金仓 KingbaseES v8
-- ============================================================
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_login_log (
    id            VARCHAR(64) PRIMARY KEY,
    corp_code     VARCHAR(64),
    operator      VARCHAR(64),    -- 操作人（t_apaas_uc_user.id 短ID）
    operator_name VARCHAR(128),   -- 操作人姓名（落库冗余）
    login_type    VARCHAR(32),    -- CORP/MOBILE_VERIFY/DDING/WX
    terminal      VARCHAR(64),    -- 登录终端
    ip            VARCHAR(64),    -- 登录IP
    session_id    VARCHAR(160),   -- 会话标识
    user_agent    VARCHAR(1024),  -- 浏览器 agent
    region        VARCHAR(128),   -- 登录地点
    login_at      TIMESTAMP,      -- 操作时间（登录时间）
    created_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_login_log_time     ON "qixiao-apaas".t_auto_hltgq_login_log(login_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_log_session  ON "qixiao-apaas".t_auto_hltgq_login_log(session_id);
CREATE INDEX IF NOT EXISTS idx_login_log_operator ON "qixiao-apaas".t_auto_hltgq_login_log(operator);
