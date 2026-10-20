-- 상장일 시초가(공모주 상장일 시가) 컬럼 추가.
-- 금융위 주식시세 API(GetStockSecuritiesInfoService_V2)에서 mkp 필드를 받아 저장하며, 데이터는 D+1 영업일 오후부터 조회된다.

ALTER TABLE ipos ADD COLUMN listing_open_price BIGINT;

COMMENT ON COLUMN ipos.listing_open_price IS '상장일 시초가(원). 금융위 주식시세 API로 상장 다음 영업일 이후 채운다. 모르면 NULL';
