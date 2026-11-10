-- 자동 재점검(공모 철회 점검 + 일정 정정 + 배포 후 자동복구)에 필요한 최소 스키마.
-- 새 수집부터 DS006 접수번호(ipos.source_receipt_no)와 청약/환불 일정 원본(last_imported_*)을 저장한다.
-- 기존 출처 불명 행은 NULL로 보호하고 백필하지 않는다. V43(last_imported_listing_date)은 수정하지 않는다.

-- ipos: 이 행의 데이터를 만든 최신 DS006 접수번호. 자동 수집 행만 채워지며, 값이 있으면 철회 점검 시
-- 같은 원공시와만 매칭한다(다른 공모 재추진 건을 덮어쓰지 않기 위함).
ALTER TABLE ipos ADD COLUMN source_receipt_no VARCHAR(20);
COMMENT ON COLUMN ipos.source_receipt_no IS '이 행의 데이터를 만든 최신 DS006 접수번호(14자리). 자동 수집 행만 저장';

-- ipos: 자동 수집이 마지막으로 반영한 청약 시작일. 현재 값과 같거나 현재 값이 NULL일 때만 다시 정정한다.
-- NULL이면 출처 불명/수동 입력으로 보고 자동 덮어쓰기 금지.
ALTER TABLE ipos ADD COLUMN last_imported_subscription_start_date DATE;
COMMENT ON COLUMN ipos.last_imported_subscription_start_date IS '자동 수집이 마지막으로 반영한 청약 시작일. 현재 값과 같으면 정정 허용, NULL이면 자동 덮어쓰기 금지';

-- ipos: 자동 수집이 마지막으로 반영한 청약 종료일. 규칙은 청약 시작일과 동일하다.
ALTER TABLE ipos ADD COLUMN last_imported_subscription_end_date DATE;
COMMENT ON COLUMN ipos.last_imported_subscription_end_date IS '자동 수집이 마지막으로 반영한 청약 종료일. 현재 값과 같으면 정정 허용, NULL이면 자동 덮어쓰기 금지';

-- ipos: 자동 수집이 마지막으로 반영한 환불일. 규칙은 청약 시작일과 동일하다.
ALTER TABLE ipos ADD COLUMN last_imported_refund_date DATE;
COMMENT ON COLUMN ipos.last_imported_refund_date IS '자동 수집이 마지막으로 반영한 환불일. 현재 값과 같으면 정정 허용, NULL이면 자동 덮어쓰기 금지';

-- ipos: corp_code 활성 유니크(V11)를 source_receipt_no 단위 유니크로 대체한다.
-- 같은 receipt(=같은 공모)의 활성 중복만 막으면 충분하고, 서로 다른 receipt의 재추진(신규 ACTIVE 건 생성)은 허용해야
-- 철회 없이 진행되는 동일 회사 재공모를 감지·보류(FR-11)할 수 있다. V6의 uk_ipo_name_start_date_active는
-- 이름+청약 시작일 기준으로 동일 공모의 중복 삽입을 계속 차단한다.
-- 동일 회사 ACTIVE 2건은 서비스가 감지해 upsert로 덮어쓰지 않고 실패 기록 후 보류한다.
DROP INDEX IF EXISTS uk_ipo_corp_code_active;
CREATE UNIQUE INDEX uk_ipo_corp_code_source_receipt_active
  ON ipos (corp_code, source_receipt_no)
  WHERE status = 'ACTIVE' AND source_receipt_no IS NOT NULL;
COMMENT ON INDEX uk_ipo_corp_code_source_receipt_active IS '활성 공모 중 (corp_code, source_receipt_no) 중복 방지. 서로 다른 접수번호의 재추진 건은 허용';

-- ipo_sync_runs: 일일 재점검(DAILY_RECONCILE)과 배포 후 자동복구(POST_DEPLOY_RECOVER)의 실행 결과 기록.
-- 부분 실패는 FAILED이며 완료가 아니다. 다음 실행(04:30 배치 또는 재기동)이 전수 재점검으로 재시도한다.
CREATE TABLE ipo_sync_runs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  run_type VARCHAR(30) NOT NULL,        -- DAILY_RECONCILE | POST_DEPLOY_RECOVER
  app_version VARCHAR(100) NOT NULL,    -- POST_DEPLOY_RECOVER의 복구 알고리즘 키('ipo-reconciliation-v1' 고정). DAILY도 같은 키를 관측용으로 기록
  status VARCHAR(10) NOT NULL,          -- SUCCESS | FAILED (부분 실패 = FAILED)
  started_at TIMESTAMP NOT NULL,        -- 실행 시작 시각(수집·재점검 합산)
  finished_at TIMESTAMP NULL,           -- 종료 시각. 미완료(충돌로 스킵 등)는 행 자체를 남기지 않는다
  total_targets INT NULL,               -- 재점검 대상(활성 자동 공모) 건수
  succeeded INT NULL,                   -- 철회 없이 유지되거나 철회 완료한 건수
  failed INT NULL,                      -- 실패 기록(건당 1) 수
  failures JSONB NULL,                  -- [{"ipoId":1,"corpCode":"...","reason":"철회점검: ..."}]
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ipo_sync_runs IS 'IPO 자동 재점검 실행 결과. POST_DEPLOY_RECOVER의 SUCCESS 행이 "배포 후 복구 완료" 판정 기준';
COMMENT ON COLUMN ipo_sync_runs.run_type IS 'DAILY_RECONCILE(04:30 배치의 재점검 단계) | POST_DEPLOY_RECOVER(재기동 후 자동복구)';
COMMENT ON COLUMN ipo_sync_runs.app_version IS 'POST_DEPLOY_RECOVER의 복구 알고리즘 키(ipo-reconciliation-v1). DAILY도 관측용으로 같은 키 기록';
COMMENT ON COLUMN ipo_sync_runs.status IS 'SUCCESS(전수 성공) | FAILED(중단 또는 부분 실패, 완료 아님)';
COMMENT ON COLUMN ipo_sync_runs.failures IS '실패 상세 JSON 배열. 철회 점검 실패는 reason이 "철회점검: "로 시작';

-- 배포 후 복구 성공은 복구 알고리즘 키당 정확히 1건만 기록한다(반복 재기동 시 중복 복구 실행 방지).
-- DAILY_RECONCILE SUCCESS는 매일 기록될 수 있어 유니크 대상에서 제외한다.
CREATE UNIQUE INDEX uk_ipo_sync_runs_recover_success
  ON ipo_sync_runs (app_version)
  WHERE run_type = 'POST_DEPLOY_RECOVER' AND status = 'SUCCESS';
