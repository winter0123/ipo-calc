# 도메인 검증 중복 정리 Breakdown

## SF-1 — 매도 금액 하한 검증 공통화

- 대상: `SaleService`, 기존 `SaleServiceTest`.
- AC: 등록·수정 모두 null 선택 필드를 허용하고 음수는 동일 문구의 validation exception으로 거부한다.
- 검증: backend 테스트 및 전체 정적 검사/build는 누적 단계 종료 때 한 번 실행한다.

## SF-2 — 배정 update 중복 음수 검사 제거

- 대상: `AllocationService.update`, 기존 Allocation controller/API 테스트.
- AC: 음수 요청 400 및 상한/이동 규칙 유지. 호출 경로상 `validateQuantity`가 하한 검사를 소유한다.
- 검증: SF-1과 함께 backend 종합 검증.

## SF-3 — 청약 수정의 중복 계좌 검증 제거

- 대상: `SubscriptionService.update`, `SubscriptionControllerTest`.
- AC: 새 `securitiesAccountId`가 주어지면 조회·소유권 검증한 `SecuritiesAccount`를 최종 investor/account 일치 확인에 재사용한다. investorId만 바뀌는 경우 기존 계좌를 조회하고 관계를 다시 확인한다.
- 계약: 다른 계정 계좌는 계속 403, 같은 계정 내 다른 투자자의 계좌는 기존 검증 오류, 합법적인 같은 투자자 계좌 변경은 성공한다. API payload/응답, 중복 청약, 배정 수량 규칙은 바꾸지 않는다.
- 검증: 기존 소유권 테스트와 새 same-investor account-switch 회귀 테스트를 추가하고 전체 종합 검증에서 실행한다.
