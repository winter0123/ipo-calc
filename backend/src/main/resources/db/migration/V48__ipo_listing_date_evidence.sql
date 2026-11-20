ALTER TABLE ipos
  ADD COLUMN listing_date_evidence_type VARCHAR(30),
  ADD COLUMN listing_date_evidence_receipt VARCHAR(20);

COMMENT ON COLUMN ipos.listing_date_evidence_type IS
  '상장일 증거 상태: ESTIMATED_DART(예정), CONFIRMED_KRX(확정)';
COMMENT ON COLUMN ipos.listing_date_evidence_receipt IS
  '상장일 근거 발행실적보고서 접수번호';
