-- subscriptions: 청약 기록 — 이미 증권사를 통해 완료된 청약 결과를 사후에 수동 입력한 사실 기록
CREATE TABLE subscriptions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,         -- 청약 고유 식별자(Long IDENTITY)
    investor_id BIGINT NOT NULL,                                -- 투자자 기준정보 참조 (FK는 다음 SF에 애플리케이션 레벨 존재 검증으로 대체)
    securities_account_id BIGINT NOT NULL,                      -- 증권 계좌 참조 (FK는 다음 SF에 애플리케이션 레벨 존재 검증으로 대체)
    ipo_id BIGINT NOT NULL,                                     -- 청약한 IPO 기준정보 참조 (FK는 다음 SF에 애플리케이션 레벨 존재 검증으로 대체)
    quantity BIGINT NOT NULL,                                   -- 청약수량(주 단위, 0 초과)
    deposit_amount BIGINT NOT NULL,                             -- 증거금 납입액(원 단위, 0 이상 — 사용자가 입력한 사실 기록)
    subscribed_at TIMESTAMP NOT NULL,                           -- 청약일시(사후 기록이므로 미래 시각도 허용)
    memo TEXT NULL,                                             -- 메모(선택, 예: "비대면 청약")
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',               -- 상태(ACTIVE/CANCELLED)
    deleted_at TIMESTAMP NULL,                                  -- 논리 삭제 시각(취소 시 설정)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,    -- 생성 일시
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP     -- 수정 일시(JPA @LastModifiedDate로 갱신)
);

COMMENT ON TABLE subscriptions IS '청약 기록 — 이미 완료된 청약 결과를 사후에 수동 입력한 기록';
COMMENT ON COLUMN subscriptions.id IS '청약 고유 식별자(Long IDENTITY)';
COMMENT ON COLUMN subscriptions.investor_id IS '투자자 기준정보 참조';
COMMENT ON COLUMN subscriptions.securities_account_id IS '증권 계좌 참조';
COMMENT ON COLUMN subscriptions.ipo_id IS '청약한 IPO 기준정보 참조';
COMMENT ON COLUMN subscriptions.quantity IS '청약수량(주 단위, 0 초과)';
COMMENT ON COLUMN subscriptions.deposit_amount IS '증거금 납입액(원 단위, 0 이상 — 사용자가 입력한 사실 기록)';
COMMENT ON COLUMN subscriptions.subscribed_at IS '청약일시(사후 기록이므로 미래 시각도 허용)';
COMMENT ON COLUMN subscriptions.memo IS '메모(선택, 예: "비대면 청약")';
COMMENT ON COLUMN subscriptions.status IS '상태(ACTIVE/CANCELLED)';
COMMENT ON COLUMN subscriptions.deleted_at IS '논리 삭제 시각(취소 시 설정)';
COMMENT ON COLUMN subscriptions.created_at IS '생성 일시';
COMMENT ON COLUMN subscriptions.updated_at IS '수정 일시(JPA @LastModifiedDate로 갱신)';

-- 활성(ACTIVE)이면서 논리 삭제되지 않은 레코드 간 (investor_id, securities_account_id, ipo_id) 조합 중복 방지
-- spec AC-005: 동일 조합의 ACTIVE 기록 중복은 409, AC-006: CANCELLED 기록이 있어도 재등록 허용
CREATE UNIQUE INDEX uk_subscription_active_combo ON subscriptions (investor_id, securities_account_id, ipo_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_subscription_investor_id ON subscriptions (investor_id);            -- 투자자별 목록 조회 최적화
CREATE INDEX idx_subscription_securities_account_id ON subscriptions (securities_account_id); -- 계좌별 목록 조회 최적화
CREATE INDEX idx_subscription_status ON subscriptions (status);                      -- 상태별 필터링 최적화