-- 임퍼소네이션 동시 실행 방지를 위한 부분 고유 인덱스 (종료되지 않은 세션만 관리자당 1개)
CREATE UNIQUE INDEX IF NOT EXISTS uq_impersonation_actor_active
    ON impersonation_sessions (actor_admin_id)
    WHERE ended_at IS NULL;