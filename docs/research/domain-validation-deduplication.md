# 도메인 검증 중복 조사

`SaleService` 등록/수정 경로가 price, amount, fee, tax, otherCost의 음수 검사를 동일한 필드명·오류 문구로 각각 반복한다. 두 경로는 선택 금액을 허용하므로 helper는 null을 건너뛰고 기존 0 이상 규칙만 공유할 수 있다.

`AllocationService.update`는 요청 수량의 음수를 먼저 검사한 뒤, 이후 이동/일반 분기에서 모두 `validateQuantity`로 같은 하한을 다시 검사한다. `updatedAllocatedQuantity != null`인 두 경로 모두 청약 조회 후 `validateQuantity`를 통과하므로 선행 하한 검사는 중복이다. API 테스트에는 음수 PATCH가 400인 회귀 검증이 있다.

Subscription의 필수/부분 수정 검증은 null 허용, 이미 배정된 수량 하한, 서로 다른 오류 문구가 있어 현 단계에서는 통합하지 않는다. 서비스 간 범용 validator나 새 주입 의존성은 만들지 않는다.

추가 조사: `SubscriptionService.update`는 요청에 새 `securitiesAccountId`가 있으면 먼저 `validateSecuritiesAccountOwnership(updatedSecuritiesAccountId)`를 호출한 뒤, 아래 최종 계좌-투자자 일치 검사에서 같은 계좌를 다시 조회·소유권 확인한다. 처음 확인한 `SecuritiesAccount`를 버리기 때문에 저장소 조회와 동일한 소유권 검증이 중복된다. 이미 조회한 계좌를 재사용해도 외부 계약은 바뀌지 않는다. investorId만 바뀌는 경우 기존 계좌를 새 투자자와 대조해야 하므로 최종 계좌 조회는 유지해야 한다.
