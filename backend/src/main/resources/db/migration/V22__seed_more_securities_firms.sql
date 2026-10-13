-- 공모주 청약을 받는 국내 증권사 추가 시드 (idempotent)
-- - V15와 같은 형식: (name, ACTIVE) 중복이면 넣지 않는다. LS증권은 다른 마이그레이션이 먼저 넣었어도 안전하다.
-- - 모두 API 연동 미지원(api_supported=FALSE)이며 사업자등록번호는 확인 전이라 NULL로 둔다.
-- - DB증권은 구 DB금융투자, iM증권은 구 하이투자증권이다.

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'LS증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'LS증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '교보증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '교보증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '현대차증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '현대차증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'IBK투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'IBK투자증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'DB증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'DB증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'iM증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'iM증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '유진투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '유진투자증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '부국증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '부국증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '상상인증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '상상인증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT 'BNK투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = 'BNK투자증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '다올투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '다올투자증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '케이프투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '케이프투자증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '한양증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '한양증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '카카오페이증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '카카오페이증권' AND status = 'ACTIVE');

INSERT INTO securities_firms (name, external_code, business_registration_number, status, api_supported, api_provider, api_auth_type, api_readonly_only, api_external_code, created_at, updated_at)
SELECT '코리아에셋투자증권', NULL, NULL, 'ACTIVE', FALSE, NULL, NULL, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = '코리아에셋투자증권' AND status = 'ACTIVE');
