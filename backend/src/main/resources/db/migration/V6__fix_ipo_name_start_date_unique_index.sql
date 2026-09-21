-- V5의 uk_ipo_name_start_date_active가 stock_code 유무를 고려하지 않아,
-- 동일 (ipo_name, subscription_start_date)이면서 stock_code가 서로 다른
-- 정당한 회차(spec AC-006)까지 차단하는 버그를 수정한다.
-- D-IPO-001: (ipo_name, subscription_start_date) 중복 검사는 stock_code가
-- NULL인 레코드에만 적용한다(Service 계층 checkDuplicate와 동일 규칙).
DROP INDEX uk_ipo_name_start_date_active;

CREATE UNIQUE INDEX uk_ipo_name_start_date_active ON ipos (ipo_name, subscription_start_date)
    WHERE status = 'ACTIVE' AND stock_code IS NULL;
