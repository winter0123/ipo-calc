-- 계정 역할 컬럼 추가. 기존 행은 모두 USER, ADMIN은 DB에서 직접 지정한다.
ALTER TABLE accounts ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_role CHECK (role IN ('USER', 'ADMIN'));
