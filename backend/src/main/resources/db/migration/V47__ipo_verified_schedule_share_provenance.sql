-- SF-4: 항목별 출처/수동값 보호. 기존 행은 추정 백필하지 않고 NULL로 보존한다.
ALTER TABLE ipos ADD COLUMN last_imported_offering_share_count BIGINT;
ALTER TABLE ipos ADD COLUMN last_verified_schedule_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN last_verified_share_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN last_verified_refund_receipt_no VARCHAR(20);

COMMENT ON COLUMN ipos.last_imported_offering_share_count IS
  '마지막 자동 수집에서 저장한 공모 주식 수. 기존 출처 불명 또는 수동 수정 값은 NULL로 보호';
COMMENT ON COLUMN ipos.last_verified_schedule_receipt_no IS
  '검증된 청약·배정 등 공시 일정의 최신 접수번호. 환불일 보호 여부는 별도 컬럼으로 판정';
COMMENT ON COLUMN ipos.last_verified_share_receipt_no IS
  '검증된 공모주식 수가 반영된 최신 공시 접수번호 (원공시 ID와 별도)';

COMMENT ON COLUMN ipos.last_verified_refund_receipt_no IS
  '실제 DART 원문 환불일이 저장된 최신 접수번호. 기존 값과 uStockPlus 보완값은 추정 백필하지 않음';
