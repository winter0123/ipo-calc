-- 키움증권·LS증권 자동 가져오기(조회 전용) 활성화 (idempotent)
-- - 기존 행은 삭제하지 않고 API 연동 컬럼만 켠다.
-- - LS증권 행이 없으면 추가한다. ACTIVE 중복 생성은 WHERE NOT EXISTS로 막는다.

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'LS증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'LS증권' AND status = 'ACTIVE');

UPDATE securities_firms
SET api_supported = TRUE, api_provider = 'KIWOOM', api_auth_type = 'APP_KEY_SECRET', api_readonly_only = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE name = '키움증권' AND status = 'ACTIVE';

UPDATE securities_firms
SET api_supported = TRUE, api_provider = 'LS', api_auth_type = 'APP_KEY_SECRET', api_readonly_only = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE name = 'LS증권' AND status = 'ACTIVE';
