-- 증권사 API 키 발급일(서비스 신청일). 한국투자증권은 사용기간이 신청일부터 1년이라 만료 안내에 쓴다.
ALTER TABLE securities_account_api_credentials
    ADD COLUMN key_issued_on DATE NOT NULL DEFAULT CURRENT_DATE;

COMMENT ON COLUMN securities_account_api_credentials.key_issued_on IS '증권사 API 키 발급일(사용자 입력, 기본은 등록일). 만료 안내 계산에 사용';
