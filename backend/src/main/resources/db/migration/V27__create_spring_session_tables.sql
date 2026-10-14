-- Spring Session JDBC 공식 PostgreSQL 스키마(세션을 DB에 저장해 재시작·재배포해도 로그인이 유지되게 한다).
-- 만료 세션은 Spring Session이 매분(기본 cron) 지운다.

CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

COMMENT ON TABLE spring_session IS '로그인 세션(Spring Session JDBC). 로그인 유지용, 만료되면 자동 삭제';
COMMENT ON COLUMN spring_session.primary_id IS 'PK. 내부 세션 식별자(세션 ID가 바뀌어도 유지)';
COMMENT ON COLUMN spring_session.session_id IS '쿠키(JSESSIONID)에 담기는 세션 ID';
COMMENT ON COLUMN spring_session.creation_time IS '세션 생성 시각(epoch 밀리초)';
COMMENT ON COLUMN spring_session.last_access_time IS '마지막 요청 시각(epoch 밀리초)';
COMMENT ON COLUMN spring_session.max_inactive_interval IS '비활동 허용 시간(초). 이 시간 동안 요청이 없으면 만료';
COMMENT ON COLUMN spring_session.expiry_time IS '만료 시각(epoch 밀리초). 요청마다 뒤로 밀린다(슬라이딩)';
COMMENT ON COLUMN spring_session.principal_name IS '로그인한 사용자(이메일). 계정별 세션 조회·강제 종료에 쓴다';
COMMENT ON TABLE spring_session_attributes IS '세션에 담긴 값(직렬화된 인증 정보 등)';
COMMENT ON COLUMN spring_session_attributes.session_primary_id IS 'spring_session.primary_id. 세션이 지워지면 함께 삭제';
COMMENT ON COLUMN spring_session_attributes.attribute_name IS '값 이름';
COMMENT ON COLUMN spring_session_attributes.attribute_bytes IS 'Java 직렬화된 값';
