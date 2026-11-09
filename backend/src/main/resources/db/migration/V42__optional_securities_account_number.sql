-- 증권계좌 계좌번호 선택적 입력(계좌 존재 여부 기반 등록/재사용)
-- account_number를 nullable로 변경, 기존 암호문/ID/FK 보존
ALTER TABLE securities_accounts ALTER COLUMN account_number DROP NOT NULL;