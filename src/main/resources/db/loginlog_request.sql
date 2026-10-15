-- 登录请求（nginx 采集的 IP/UA/时间，待与登录事件按时间合并）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_login_request (
    id         VARCHAR(64) PRIMARY KEY,
    ip         VARCHAR(64),
    user_agent VARCHAR(1024),
    login_at   TIMESTAMP,
    matched    VARCHAR(2) DEFAULT '0',
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_login_request_time    ON "qixiao-apaas".t_auto_hltgq_login_request(login_at);
CREATE INDEX IF NOT EXISTS idx_login_request_matched ON "qixiao-apaas".t_auto_hltgq_login_request(matched);
