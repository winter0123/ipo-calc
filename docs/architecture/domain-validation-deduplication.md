# 도메인 검증 중복 정리 구조

새 계층 없이 각 도메인 서비스의 기존 경계 안에서 중복만 제거한다.

```text
SaleService.register ─┐
                     ├─ validateNonNegativeAmounts → 동일 필드별 검증/예외
SaleService.update ──┘

AllocationService.update → validateQuantity → 음수 및 청약수량 상한 검증

SubscriptionService.update(accountId 제공)
  → SecuritiesAccount 한 번 조회 + 계정 소유권 검증
  → 같은 검증 결과로 최종 investor/account 관계 확인

SubscriptionService.update(investorId만 제공)
  → 기존 SecuritiesAccount 조회 + 새 investor/account 관계 확인
```

등록/수정의 필수값과 quantity 규칙은 각 request 경로에 유지한다. Allocation update의 `validateQuantity`는 이동과 비이동 경로에서 이미 호출되므로 별도 validator를 추가하지 않는다.
