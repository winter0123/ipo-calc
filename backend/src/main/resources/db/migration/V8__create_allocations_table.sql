-- allocations: 배정 결과 기록 — 이미 증권사를 통해 확정된 배정 결과를 사후에 수동 입력한 사실 기록
CREATE TABLE allocations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,          -- 배정 고유 식별자(Long IDENTITY)
    subscription_id BIGINT NOT NULL,                             -- 연결된 청약 기록 참조 (1:1, FK 제약으로 참조 무결성 보장)
    allocated_quantity BIGINT NOT NULL,                          -- 배정수량(주 단위, 0 이상)
    allocated_at TIMESTAMP NOT NULL,                             -- 배정일시(사후 기록이므로 과거/현재/미래 시각 모두 허용)
    allocation_method VARCHAR(255) NULL,                         -- 배정방식메모(선택, 자유 텍스트 — Enum 아님, 예: "균등배정")
    allocation_price BIGINT NULL,                                -- 배정단가(원 단위, 0 이상, 선택)
    note VARCHAR(255) NULL,                                      -- 비고(선택)
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',                -- 상태(ACTIVE/CANCELLED)
    deleted_at TIMESTAMP NULL,                                   -- 논리 삭제 시각(취소 시 설정)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,     -- 생성 일시
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP      -- 수정 일시(JPA @LastModifiedDate로 갱신)
);

COMMENT ON TABLE allocations IS '배정 결과 기록 — 이미 확정된 배정 결과를 사후에 수동 입력한 기록';
COMMENT ON COLUMN allocations.id IS '배정 고유 식별자(Long IDENTITY)';
COMMENT ON COLUMN allocations.subscription_id IS '연결된 청약 기록 참조 (1:1)';
COMMENT ON COLUMN allocations.allocated_quantity IS '배정수량(주 단위, 0 이상)';
COMMENT ON COLUMN allocations.allocated_at IS '배정일시(사후 기록이므로 과거/현재/미래 시각 모두 허용)';
COMMENT ON COLUMN allocations.allocation_method IS '배정방식메모(선택, 자유 텍스트 — Enum 아님)';
COMMENT ON COLUMN allocations.allocation_price IS '배정단가(원 단위, 0 이상, 선택)';
COMMENT ON COLUMN allocations.note IS '비고(선택)';
COMMENT ON COLUMN allocations.status IS '상태(ACTIVE/CANCELLED)';
COMMENT ON COLUMN allocations.deleted_at IS '논리 삭제 시각(취소 시 설정)';
COMMENT ON COLUMN allocations.created_at IS '생성 일시';
COMMENT ON COLUMN allocations.updated_at IS '수정 일시(JPA @LastModifiedDate로 갱신)';

-- 외래 키 제약: 물리 삭제를 막아 참조 무결성 보장(취소는 논리 삭제이므로 항상 성공)
ALTER TABLE allocations
    ADD CONSTRAINT fk_allocation_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;

-- 활성(ACTIVE)이면서 논리 삭제되지 않은 레코드 간 subscription_id 중복 방지 (1:1 보장)
-- spec AC-005: 동일 subscriptionId의 ACTIVE 기록 중복은 409, AC-006: CANCELLED 기록이 있어도 재등록 허용
CREATE UNIQUE INDEX uk_allocation_active_subscription ON allocations (subscription_id)
    WHERE status = 'ACTIVE';

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_allocation_subscription_id ON allocations (subscription_id);  -- 청약별 조회 최적화
CREATE INDEX idx_allocation_status ON allocations (status);                    -- 상태별 필터링 최적화
