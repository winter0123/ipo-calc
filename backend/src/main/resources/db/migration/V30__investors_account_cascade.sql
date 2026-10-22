-- 계정 하드 삭제(테스트 tearDown 등) 시 자동 생성된 본인 투자자도 같이 지운다.
ALTER TABLE investors DROP CONSTRAINT investors_account_id_fkey;
ALTER TABLE investors
  ADD CONSTRAINT investors_account_id_fkey
  FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE;
