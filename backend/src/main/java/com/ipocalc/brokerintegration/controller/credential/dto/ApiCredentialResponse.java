package com.ipocalc.brokerintegration.controller.credential.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 브로커 API 인증정보 응답 DTO.
 *
 * <p>연결 상태, 마스킹된 계좌번호, 마지막 동기화 시각만 노출한다. 연결 상태는 {@code CONNECTED}, {@code EXPIRED}, {@code ERROR},
 * {@code DISCONNECTED} 중 하나다. 앱키·앱시크릿·접근토큰·만료 시각 같은 시크릿 필드는 이 DTO에 존재하지 않는다(AC-1811).
 *
 * @param id 인증정보 ID (연결 전이면 null)
 * @param securitiesAccountId 대상 증권계좌 ID
 * @param apiProvider 브로커 어댑터 식별자 (예: KIS, 연결 전이면 null)
 * @param connectionStatus 연결 상태 (CONNECTED, EXPIRED, ERROR, DISCONNECTED)
 * @param accountNumber 마스킹된 증권계좌번호
 * @param lastSyncedAt 마지막 동기화 시각 (접근토큰이 한 번도 캐시되지 않았으면 null)
 * @param supportedProvider 이 증권계좌의 증권사가 지원하는 어댑터 식별자 (미지원이면 null)
 * @param keyIssuedOn 증권사 키 발급일 (연결 전이면 null)
 * @param keyExpiresOn 증권사 서비스 사용기간 만료일 (발급일 + 1년, 연결 전이면 null)
 */
public record ApiCredentialResponse(
    Long id,
    Long securitiesAccountId,
    String apiProvider,
    String connectionStatus,
    String accountNumber,
    LocalDateTime lastSyncedAt,
    String supportedProvider,
    LocalDate keyIssuedOn,
    LocalDate keyExpiresOn) {}
