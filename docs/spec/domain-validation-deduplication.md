# 도메인 검증 중복 정리 Spec

## 범위

- `SaleService`의 등록/수정 금액 하한 검사를 private helper로 공유한다.
- `AllocationService.update`에서 후속 `validateQuantity`가 보장하는 중복 음수 검사만 제거한다.
- `SubscriptionService.update`에서 새 계좌 ID를 검증할 때 조회한 `SecuritiesAccount` 객체를 최종 투자자-계좌 일치 확인에 재사용한다.

## Acceptance Criteria

1. 매도 금액 필드의 null 허용 및 음수 거부, 기존 필드별 오류 문구를 보존한다.
2. 배정 음수 수량 PATCH는 기존과 같이 HTTP 400이고, 음수가 아닌 수량 검증은 유지된다.
3. 소유권 검사, 오류 응답, 트랜잭션 및 데이터 변경 동작은 바뀌지 않는다.
4. 계좌 ID 수정 시 계좌를 한 번 검증한 결과를 재사용하고, 최종 investor/account 관계 검증은 유지한다. investorId만 수정하는 경우 기존 계좌를 조회해 새 투자자와 대조한다.

## 비범위

청약/배정/매도 API 계약 변경, 공통 validation framework, 다른 필드 정책 통합. 이 변경은 쿼리/검증 중복만 줄이고 청약의 부분 수정 규칙은 통합하지 않는다.
