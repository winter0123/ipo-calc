-- costs: 청약/취득 단계 비용 기록 — 이미 발생한 비용을 사용자가 사후에 수동 입력한 사실 기록
CREATE TABLE costs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,                -- 비용 고유 식별자(Long IDENTITY)
    subscription_id BIGINT NOT NULL,                                   -- 연결된 청약 기록 참조 (N:1, FK 제약으로 참조 무결성 보장)
    cost_type VARCHAR(255) NOT NULL,                                   -- 비용종류 (자유 텍스트 — "청약수수료", "계좌개설비" 등, Enum 아님)
    amount BIGINT NOT NULL,                                            -- 금액 (원 단위, 0 이상)
    incurred_at TIMESTAMP NOT NULL,                                    -- 발생일시 (사후 기록이므로 과거/현재/미래 시각 모두 허용)
    memo VARCHAR(255) NULL,                                            -- 메모 (선택, 255자 이하)
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',                      -- 상태 (ACTIVE/CANCELLED만 사용)
    deleted_at TIMESTAMP NULL,                                         -- 논리 삭제 시각 (취소 시 설정)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,           -- 생성 일시
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP            -- 수정 일시 (JPA @LastModifiedDate로 갱신)
);

COMMENT ON TABLE costs IS '청약/취득 단계 비용 기록 — 이미 발생한 비용을 사용자가 사후에 수동 입력한 사실 기록';
COMMENT ON COLUMN costs.id IS '비용 고유 식별자(Long IDENTITY)';
COMMENT ON COLUMN costs.subscription_id IS '연결된 청약 기록 참조 (N:1)';
COMMENT ON COLUMN costs.cost_type IS '비용종류 (자유 텍스트 — Enum 아님, 예: 청약수수료, 계좌개설비)';
COMMENT ON COLUMN costs.amount IS '금액 (원 단위, 0 이상)';
COMMENT ON COLUMN costs.incurred_at IS '발생일시 (사후 기록이므로 과거/현재/미래 시각 모두 허용)';
COMMENT ON COLUMN costs.memo IS '메모 (선택, 255자 이하)';
COMMENT ON COLUMN costs.status IS '상태 (ACTIVE/CANCELLED만 사용)';
COMMENT ON COLUMN costs.deleted_at IS '논리 삭제 시각 (취소 시 설정)';
COMMENT ON COLUMN costs.created_at IS '생성 일시';
COMMENT ON COLUMN costs.updated_at IS '수정 일시 (JPA @LastModifiedDate로 갱신)';

-- 외래 키 제약: 물리 삭제를 막아 참조 무결성 보장 (취소는 논리 삭제이므로 항상 성공)
ALTER TABLE costs
    ADD CONSTRAINT fk_cost_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_cost_subscription_id ON costs (subscription_id);  -- 청약별 조회 최적화
CREATE INDEX idx_cost_status ON costs (status);                    -- 상태별 필터링 최적화