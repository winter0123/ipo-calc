-- 웹 푸시 알림: 기기별 구독, 계정별 알림 종류 설정, 중복 발송 방지 기록.

CREATE TABLE push_subscriptions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    endpoint VARCHAR(1000) NOT NULL,
    p256dh VARCHAR(200) NOT NULL,
    auth VARCHAR(100) NOT NULL,
    user_agent VARCHAR(300) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP NULL,
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscription_account ON push_subscriptions (account_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE push_subscriptions IS '웹 푸시 구독. 브라우저(기기) 하나가 한 행이며 계정이 소유한다';
COMMENT ON COLUMN push_subscriptions.id IS 'PK. DB가 자동으로 매기는 번호';
COMMENT ON COLUMN push_subscriptions.account_id IS '구독을 소유한 로그인 계정 ID(accounts.id)';
COMMENT ON COLUMN push_subscriptions.endpoint IS '브라우저 푸시 서비스 주소(https). 기기마다 유일하며 비밀로 취급한다';
COMMENT ON COLUMN push_subscriptions.p256dh IS '브라우저 공개키(메시지 암호화용, Base64URL)';
COMMENT ON COLUMN push_subscriptions.auth IS '구독 인증 비밀값(메시지 암호화용, Base64URL)';
COMMENT ON COLUMN push_subscriptions.user_agent IS '구독한 브라우저 User-Agent(기기 구분용, 선택)';
COMMENT ON COLUMN push_subscriptions.status IS '레코드 상태. ACTIVE(사용 중) / CANCELLED(해제·만료됨). 물리 삭제는 하지 않는다';
COMMENT ON COLUMN push_subscriptions.deleted_at IS '소프트 삭제 시각. NULL이면 살아 있는 레코드';
COMMENT ON COLUMN push_subscriptions.created_at IS '생성 시각';
COMMENT ON COLUMN push_subscriptions.updated_at IS '마지막 수정 시각';
COMMENT ON COLUMN push_subscriptions.last_success_at IS '마지막으로 푸시 발송에 성공한 시각';

CREATE TABLE notification_preferences (
    account_id BIGINT PRIMARY KEY REFERENCES accounts (id),
    subscription_deadline BOOLEAN NOT NULL DEFAULT TRUE,
    listing_day BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE notification_preferences IS '계정별 알림 종류 설정. 행이 없으면 모두 켜짐으로 본다';
COMMENT ON COLUMN notification_preferences.account_id IS 'PK이자 계정 ID(accounts.id)';
COMMENT ON COLUMN notification_preferences.subscription_deadline IS '청약 마감 당일 알림 사용 여부';
COMMENT ON COLUMN notification_preferences.listing_day IS '상장일 알림(남은 수량이 있는 배정 대상) 사용 여부';
COMMENT ON COLUMN notification_preferences.updated_at IS '마지막 수정 시각';

CREATE TABLE notification_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    type VARCHAR(30) NOT NULL,
    ref_id BIGINT NOT NULL,
    sent_on DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_log UNIQUE (account_id, type, ref_id, sent_on)
);

COMMENT ON TABLE notification_logs IS '알림 발송 기록. 같은 날 같은 대상에 중복 발송하지 않으려고 쓴다';
COMMENT ON COLUMN notification_logs.id IS 'PK. DB가 자동으로 매기는 번호';
COMMENT ON COLUMN notification_logs.account_id IS '알림을 받은 계정 ID(accounts.id)';
COMMENT ON COLUMN notification_logs.type IS '알림 종류. SUBSCRIPTION_DEADLINE(청약 마감) / LISTING_DAY(상장일)';
COMMENT ON COLUMN notification_logs.ref_id IS '알림 대상 공모주 ID(ipos.id)';
COMMENT ON COLUMN notification_logs.sent_on IS '발송 날짜(한국 시간 기준)';
COMMENT ON COLUMN notification_logs.created_at IS '생성 시각';
