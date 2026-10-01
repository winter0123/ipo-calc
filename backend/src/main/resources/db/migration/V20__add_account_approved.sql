-- 비공개 운영: 가입 승인 여부. 기존 계정은 모두 승인된 것으로 둔다.
ALTER TABLE accounts ADD COLUMN approved BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN accounts.approved IS '관리자 승인 여부. 가입 모드가 APPROVAL이면 가입 시 FALSE이고 승인되기 전에는 로그인할 수 없다';
