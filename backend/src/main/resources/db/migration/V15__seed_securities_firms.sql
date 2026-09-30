-- 증권사 시드 데이터 (idempotent)
-- 목적: 증권사 공통코드 역할을 위한 기준 데이터를 적재한다.
-- - uk_securities_firm_name_active (name, status) 유니크 제약을 이용해 중복 실행을 방지한다.
-- - 기존 INACTIVE 행이 있어도 ACTIVE 중복 생성을 막기 위해 WHERE NOT EXISTS 조건을 사용한다.
-- - 사용자가 이미 만든 ACTIVE 행은 삭제·병합하지 않고 그대로 둔다.
-- - 정식 명칭·사업자등록번호는 권위 있는 출처에서 확인 전이므로 business_registration_number는 NULL로 둔다.
-- - api_provider는 실제 어댑터가 있는 증권사(한국투자증권)에만 채운다.

-- 한국투자증권 (파일럿 대상, api_supported=TRUE, api_provider='KIS')
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '한국투자증권', NULL, NULL, 'ACTIVE', TRUE, 'KIS', 'APP_KEY_SECRET', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '한국투자증권' AND status = 'ACTIVE');

-- 키움증권 (REST API 미확인, 정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '키움증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '키움증권' AND status = 'ACTIVE');

-- NH투자증권 (한국투자증권과 관계 미확인, 정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'NH투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'NH투자증권' AND status = 'ACTIVE');

-- 미래에셋증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '미래에셋증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '미래에셋증권' AND status = 'ACTIVE');

-- 삼성증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '삼성증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '삼성증권' AND status = 'ACTIVE');

-- KB증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'KB증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'KB증권' AND status = 'ACTIVE');

-- 신한투자증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '신한투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '신한투자증권' AND status = 'ACTIVE');

-- 하나증권 (정식 명칭 미확인 — '하나금융투자증권'일 가능성)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '하나증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '하나증권' AND status = 'ACTIVE');

-- 대신증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '대신증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '대신증권' AND status = 'ACTIVE');

-- 유안타증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '유안타증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '유안타증권' AND status = 'ACTIVE');

-- 한화투자증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '한화투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '한화투자증권' AND status = 'ACTIVE');

-- SK투자증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'SK투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'SK투자증권' AND status = 'ACTIVE');

-- 메리츠증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '메리츠증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '메리츠증권' AND status = 'ACTIVE');

-- 토스증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '토스증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '토스증권' AND status = 'ACTIVE');

-- 신영증권 (정식 명칭 미확인)
INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '신영증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '신영증권' AND status = 'ACTIVE');