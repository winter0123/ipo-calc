-- 가격 출처 추적 컬럼 추가
ALTER TABLE ipos ADD COLUMN band_price_source_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN band_price_parser_version INTEGER;
ALTER TABLE ipos ADD COLUMN fixed_price_source_receipt_no VARCHAR(20);
ALTER TABLE ipos ADD COLUMN fixed_price_parser_version INTEGER;

-- 기존 잘못된 데이터 정리: lower == upper인 밴드는 유효하지 않으므로 NULL로 초기화
-- (단, fixed_price가 같은 값이면 그 값은 확정가로 보존)
UPDATE ipos
SET lower_band_price = NULL,
    upper_band_price = NULL
WHERE lower_band_price IS NOT NULL
  AND upper_band_price IS NOT NULL
  AND lower_band_price = upper_band_price;

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