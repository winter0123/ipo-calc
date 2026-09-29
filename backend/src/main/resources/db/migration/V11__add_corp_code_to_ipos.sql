-- OpenDART 기업 고유번호(corp_code) 컬럼 추가
-- 자동 동기화(SF-3)가 종목코드 없는 신규 상장 전 기업을 식별하는 키로 사용
ALTER TABLE ipos ADD COLUMN corp_code VARCHAR(8) NULL;

-- 활성(ACTIVE) 레코드 간 corp_code 중복 방지 (부분 유니크, corp_code는 NULL이면 제외)
-- 기존 uk_ipo_stock_code_active 패턴 그대로 적용
CREATE UNIQUE INDEX uk_ipo_corp_code_active ON ipos (corp_code)
    WHERE status = 'ACTIVE' AND corp_code IS NOT NULL;

-- 기존 행은 corp_code NULL 유지(수동 등록 데이터는 영향 없음)