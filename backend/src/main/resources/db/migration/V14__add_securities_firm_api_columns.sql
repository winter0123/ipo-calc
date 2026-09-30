-- 증권사 API 연동 메타데이터 컬럼 추가
-- 목적: securities_firms 테이블에 조회 전용 API 연동에 필요한 메타데이터를 저장하기 위한 컬럼을 추가한다.
-- 기존 행이 있을 수 있으므로 NOT NULL 컬럼에는 DEFAULT 값을 지정해 기존 행이 기본값으로 채워지게 한다.

ALTER TABLE securities_firms
    ADD COLUMN api_supported BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN api_provider VARCHAR(50),
    ADD COLUMN api_auth_type VARCHAR(50),
    ADD COLUMN api_readonly_only BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN api_external_code VARCHAR(255);

-- 컬럼 설명
COMMENT ON COLUMN securities_firms.api_supported IS '조회 전용 API 연동 대상 증권사 여부 (TRUE: 연동 대상, FALSE: 미대상)';
COMMENT ON COLUMN securities_firms.api_provider IS '사용할 브로커 어댑터 식별자 (예: KIS)';
COMMENT ON COLUMN securities_firms.api_auth_type IS '인증 방식 (파일럿은 APP_KEY_SECRET, 유형 확장 대비)';
COMMENT ON COLUMN securities_firms.api_readonly_only IS '이 서비스가 해당 증권사를 조회 전용으로만 사용함을 명시 (기본 TRUE)';
COMMENT ON COLUMN securities_firms.api_external_code IS '브로커 측 증권사 식별 코드 (external_code와 용도 분리)';