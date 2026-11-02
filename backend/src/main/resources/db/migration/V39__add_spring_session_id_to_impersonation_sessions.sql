-- 만료 처리에서 임퍼소네이션 UUID와 별개인 Spring Session의 마커만 제거할 수 있도록 연결 키를 보관한다.
ALTER TABLE impersonation_sessions
    ADD COLUMN spring_session_id VARCHAR(100);

COMMENT ON COLUMN impersonation_sessions.spring_session_id IS '임퍼소네이션 마커를 보관한 Spring Session ID';
