-- 가격 출처 추적 컬럼 추가
ALTER TABLE ipos ADD COLUMN band_price_source_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN band_price_parser_version INTEGER;
ALTER TABLE ipos ADD COLUMN fixed_price_source_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN fixed_price_parser_version INTEGER;

-- 밴드 제약: 둘 다 NULL이거나 둘 다 있고 lower < upper
ALTER TABLE ipos ADD CONSTRAINT chk_band_price_valid
CHECK (
  (lower_band_price IS NULL AND upper_band_price IS NULL)
  OR (
    lower_band_price > 0
    AND upper_band_price > 0
    AND lower_band_price < upper_band_price
  )
);

-- 확정가 제약: NULL이거나 양수
ALTER TABLE ipos ADD CONSTRAINT chk_fixed_price_positive
CHECK (fixed_price IS NULL OR fixed_price > 0);