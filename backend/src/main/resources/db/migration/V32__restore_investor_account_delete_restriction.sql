-- 계정 삭제 시 투자자를 연쇄 삭제하지 않도록 기존 외래 키 정책을 유지한다.
ALTER TABLE investors DROP CONSTRAINT investors_account_id_fkey;
ALTER TABLE investors
  ADD CONSTRAINT investors_account_id_fkey
  FOREIGN KEY (account_id) REFERENCES accounts (id);
