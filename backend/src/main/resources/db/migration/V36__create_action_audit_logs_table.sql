-- 감사 로그 테이블 생성
CREATE TABLE action_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_admin_id BIGINT NOT NULL,
    subject_account_id BIGINT,
    action VARCHAR(50) NOT NULL,
    reason VARCHAR(50),
    impersonation_session_id VARCHAR(36),
    detail VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_actor_time ON action_audit_logs (actor_admin_id, created_at);
CREATE INDEX idx_audit_subject_time ON action_audit_logs (subject_account_id, created_at);
CREATE INDEX idx_audit_action_time ON action_audit_logs (action, created_at);