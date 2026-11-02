-- 임퍼소네이션 세션 테이블 생성
CREATE TABLE impersonation_sessions (
    id BIGSERIAL PRIMARY KEY,
    actor_admin_id BIGINT NOT NULL,
    subject_account_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL UNIQUE,
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    ended_reason VARCHAR(20),
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_impersonation_actor_active ON impersonation_sessions (actor_admin_id, ended_at) WHERE ended_at IS NULL;
CREATE INDEX idx_impersonation_session_id ON impersonation_sessions (session_id);