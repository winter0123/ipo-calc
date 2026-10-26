# 증권계좌 소유권 검증 경계 Spec

## 목표

SecuritiesAccountService에 흩어진 investor 조회 및 소유권 검증을 전담 verifier로 모아 보안 규칙의 위치를 명확히 한다.

## 범위

- 계좌를 통한 소유권 확인과 투자자 ID 직접 소유권 확인을 새 verifier로 이동한다.
- 기존 not-found/forbidden 예외, repository 접근 방식, 실행 순서를 유지한다.
- 계좌 서비스가 verifier를 호출하도록 바꾼다.

## 비범위

기본 계좌 전환, 계좌번호 암호화·마스킹, 계좌 CRUD, DTO 매핑, repository 쿼리, API 상태/본문, DB 변경.

## Acceptance Criteria

1. 타 계정의 계좌와 투자자 접근은 기존 계좌 도메인 403이다.
2. 없는 계좌/투자자 응답은 기존 404 및 메시지 계약을 유지한다.
3. account ID → investor lookup → `OwnershipGuard` 순서가 유지된다.
4. `getAccountNumber`는 verifier 완료 후에만 복호화하고 감사 로그를 남긴다.
5. 계좌 서비스의 investor repository/ownership guard 직접 의존성이 제거되고 관련 테스트가 통과한다.
