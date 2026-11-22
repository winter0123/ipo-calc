ALTER TABLE ipos
  ADD COLUMN krx_listing_notice_url VARCHAR(500);

COMMENT ON COLUMN ipos.krx_listing_notice_url IS
  'KRX 추가상장 공고 URL (CONFIRMED_KRX 증거 참조)';
