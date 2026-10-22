package com.ipocalc.accountauthentication.domain.event;

/**
 * 회원가입이 성공해 계정이 저장된 뒤 발행하는 이벤트.
 *
 * <p>같은 트랜잭션에서 본인 투자자 자동 생성이 이 이벤트를 구독한다.
 */
public record AccountSignedUpEvent(Long accountId, String displayName) {}
