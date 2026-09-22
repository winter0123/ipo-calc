-- sales: 분할 매도 기록 — 이미 증권사를 통해 완료된 매도 결과를 사후에 수동 입력한 사실 기록
CREATE TABLE sales (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,              -- 매도 고유 식별자(Long IDENTITY)
    allocation_id BIGINT NOT NULL,                                   -- 연결된 배정 기록 참조 (N:1, FK 제약으로 참조 무결성 보장, 유니크 아님 — 하나의 배정에 여러 매도 가능, D-SL-006)
    quantity BIGINT NOT NULL,                                        -- 매도수량(주 단위, 0 초과)
    price BIGINT NULL,                                               -- 매도단가(원 단위, 0 이상, 선택 — D-SL-007)
    amount BIGINT NULL,                                              -- 매도금액(원 단위, 0 이상, 선택 — D-SL-007)
    sale_date TIMESTAMP NOT NULL,                                    -- 매도일시(사후 기록이므로 과거/현재/미래 시각 모두 허용)
    fee BIGINT NULL,                                                 -- 수수료(원 단위, 선택 — D-SL-007)
    tax BIGINT NULL,                                                 -- 세금(원 단위, 선택 — D-SL-007)
    other_cost BIGINT NULL,                                          -- 기타비용(원 단위, 선택 — D-SL-007)
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',                    -- 상태(ACTIVE/CANCELLED만 사용 — D-SL-001)
    deleted_at TIMESTAMP NULL,                                       -- 논리 삭제 시각(취소 시 설정)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,         -- 생성 일시
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP          -- 수정 일시(JPA @LastModifiedDate로 갱신)
);

COMMENT ON TABLE sales IS '분할 매도 기록 — 이미 증권사를 통해 완료된 매도 결과를 사후에 수동 입력한 사실 기록';
COMMENT ON COLUMN sales.id IS '매도 고유 식별자(Long IDENTITY)';
COMMENT ON COLUMN sales.allocation_id IS '연결된 배정 기록 참조 (N:1, 유니크 아님 — 하나의 배정에 여러 매도 가능)';
COMMENT ON COLUMN sales.quantity IS '매도수량(주 단위, 0 초과)';
COMMENT ON COLUMN sales.price IS '매도단가(원 단위, 0 이상, 선택)';
COMMENT ON COLUMN sales.amount IS '매도금액(원 단위, 0 이상, 선택)';
COMMENT ON COLUMN sales.sale_date IS '매도일시(사후 기록이므로 과거/현재/미래 시각 모두 허용)';
COMMENT ON COLUMN sales.fee IS '수수료(원 단위, 선택)';
COMMENT ON COLUMN sales.tax IS '세금(원 단위, 선택)';
COMMENT ON COLUMN sales.other_cost IS '기타비용(원 단위, 선택)';
COMMENT ON COLUMN sales.status IS '상태(ACTIVE/CANCELLED만 사용)';
COMMENT ON COLUMN sales.deleted_at IS '논리 삭제 시각(취소 시 설정)';
COMMENT ON COLUMN sales.created_at IS '생성 일시';
COMMENT ON COLUMN sales.updated_at IS '수정 일시(JPA @LastModifiedDate로 갱신)';

-- 외래 키 제약: 물리 삭제를 막아 참조 무결성 보장(취소는 논리 삭제이므로 항상 성공)
ALTER TABLE sales
    ADD CONSTRAINT fk_sale_allocation
    FOREIGN KEY (allocation_id) REFERENCES allocations (id)
    ON DELETE RESTRICT;

-- 조회/필터링/정렬 최적화 인덱스
CREATE INDEX idx_sale_allocation_id ON sales (allocation_id);  -- 배정별 조회 및 누적 매도수량 합계 조회 최적화
CREATE INDEX idx_sale_status ON sales (status);                -- 상태별 필터링 최적화
CREATE INDEX idx_sale_sale_date ON sales (sale_date);          -- 목록 정렬(saleDate 내림차순) 최적화