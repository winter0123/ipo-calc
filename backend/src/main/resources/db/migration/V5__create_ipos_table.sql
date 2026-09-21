CREATE TABLE ipos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    ipo_name VARCHAR(255) NOT NULL,
    stock_code VARCHAR(255) NULL,
    round_label VARCHAR(255) NULL,
    lower_band_price BIGINT NULL,
    upper_band_price BIGINT NULL,
    fixed_price BIGINT NULL,
    subscription_start_date DATE NOT NULL,
    subscription_end_date DATE NOT NULL,
    listing_date DATE NULL,
    offering_share_count BIGINT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 활성(ACTIVE) 레코드 간 stock_code 중복 방지 (부분 유니크, stock_code는 NULL이면 제외)
CREATE UNIQUE INDEX uk_ipo_stock_code_active ON ipos (stock_code)
    WHERE status = 'ACTIVE' AND stock_code IS NOT NULL;

-- 활성(ACTIVE) 레코드 간 (ipo_name, subscription_start_date) 조합 중복 방지 (부분 유니크)
CREATE UNIQUE INDEX uk_ipo_name_start_date_active ON ipos (ipo_name, subscription_start_date)
    WHERE status = 'ACTIVE';

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_ipo_status ON ipos (status);