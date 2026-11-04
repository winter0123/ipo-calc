-- 증권계좌 기본 계좌(is_default) 기능 제거: partial unique index와 컬럼 삭제
DROP INDEX IF EXISTS uk_securities_account_default;
ALTER TABLE securities_accounts DROP COLUMN IF EXISTS is_default;
