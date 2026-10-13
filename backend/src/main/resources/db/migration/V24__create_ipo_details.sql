-- 공모주 상세 정보(금융감독원 DART 공시에서 가져온 값). 공모주당 1행.
-- 재동기화 때 같은 행을 덮어쓰는 스냅샷이라 소프트 삭제(deleted_at)·상태 컬럼을 두지 않는다.
CREATE TABLE ipo_details (
    ipo_id BIGINT PRIMARY KEY REFERENCES ipos (id),
    source_rcept_no VARCHAR(14) NULL,
    source_report_name VARCHAR(200) NULL,
    synced_at TIMESTAMP NULL,
    ceo_name VARCHAR(200) NULL,
    established_on DATE NULL,
    address VARCHAR(500) NULL,
    par_value BIGINT NULL,
    capital_stock BIGINT NULL,
    largest_shareholder_name VARCHAR(200) NULL,
    largest_shareholder_ratio NUMERIC(7, 2) NULL,
    institutional_competition_rate NUMERIC(12, 2) NULL,
    lock_up_commitment_ratio NUMERIC(7, 2) NULL,
    financials JSONB NOT NULL DEFAULT '[]',
    float_schedule JSONB NOT NULL DEFAULT '[]',
    underwriters JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE ipo_details IS '공모주 상세 정보(DART 공시 스냅샷, 공모주당 1행)';
COMMENT ON COLUMN ipo_details.ipo_id IS '공모주 ID(ipos.id). PK이자 FK';
COMMENT ON COLUMN ipo_details.source_rcept_no IS '값을 가져온 공시의 접수번호(14자리)';
COMMENT ON COLUMN ipo_details.source_report_name IS '값을 가져온 공시 이름. 예: [기재정정]투자설명서';
COMMENT ON COLUMN ipo_details.synced_at IS '공시에서 마지막으로 가져온 시각';
COMMENT ON COLUMN ipo_details.ceo_name IS '대표이사 이름';
COMMENT ON COLUMN ipo_details.established_on IS '설립일';
COMMENT ON COLUMN ipo_details.address IS '본점 주소';
COMMENT ON COLUMN ipo_details.par_value IS '액면가(원)';
COMMENT ON COLUMN ipo_details.capital_stock IS '자본금(원, 요약재무 최근 기)';
COMMENT ON COLUMN ipo_details.largest_shareholder_name IS '최대주주 이름';
COMMENT ON COLUMN ipo_details.largest_shareholder_ratio IS '최대주주 공모 전 지분율(%)';
COMMENT ON COLUMN ipo_details.institutional_competition_rate IS '수요예측 기관 경쟁률(X:1의 X)';
COMMENT ON COLUMN ipo_details.lock_up_commitment_ratio IS '의무보유확약 비율(%)';
COMMENT ON COLUMN ipo_details.financials IS '요약 재무 배열(공시 순서, 금액은 원 단위)';
COMMENT ON COLUMN ipo_details.float_schedule IS '유통가능 물량 일정 배열(누적 주식수·비율)';
COMMENT ON COLUMN ipo_details.underwriters IS '인수회사 배열(이름·역할·인수 주식수)';
COMMENT ON COLUMN ipo_details.created_at IS '생성 시각';
COMMENT ON COLUMN ipo_details.updated_at IS '마지막 수정 시각';
