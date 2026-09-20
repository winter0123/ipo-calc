# 작업 로그: 웹 매도·비용·수익 확인 (web-sale-profit-workflow, Epic 15)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 최신화)

`docs/research/web-sale-profit-workflow.md`는 Epic 8/9/10 확정 전 초기
조사 결론(CalculationTrace, 취득가액 선택 옵션, 매도 순서 필드 등)을
그대로 인용하고 있어, 이미 확정된 백엔드 spec 기준으로 정렬한다.

### D-W15-001: 수익 결과는 CalculationTrace 없이 API 응답 필드를 그대로 표시
[[profit-calculation]] D-P-001에 따라 Profit은 저장하지 않는 파생값이며
출처 메타데이터/정책버전 인프라가 없다. 프론트는 `GET /sales/{saleId}/profit`
응답의 `saleAmount`, `matchedAcquisitionAmount`, `matchedCostAmount`,
`saleFee`, `saleTax`, `saleOtherCost`, `preTaxProfit`, `afterTaxProfit`
필드를 계산 근거로 그대로 표시한다 — 각 차감 항목 자체가 근거이므로
별도 "계산 이력 보기" 화면을 만들지 않는다.

### D-W15-002: 취득가액 산정 기준은 이미 확정됨 (선택 UI 없음)
[[profit-calculation]] D-P-005에 따라 배정금액 단일 기준으로 이미
확정되었다. "사용자가 취득가액 기준을 선택하는 화면/옵션"은 설계하지
않는다.

### D-W15-003: 매도 순서는 saleDate 기준 정렬만 사용
[[sale-management]] D-SL-002에 따라 별도 순번 입력 필드 없이
`saleDate` + `createdAt` 정렬로 순서를 표현한다. 매도 등록 폼에
"순서 번호" 입력 필드를 만들지 않는다.

### D-W15-004: 동시성 락 UI 불필요
[[sale-management]] D-SL-004와 동일 원칙 — 서버가 표준 트랜잭션으로
누적 매도수량을 검증하므로, 프론트에 낙관적 락 충돌 처리 UI(재시도
다이얼로그 등)를 설계하지 않는다. 서버 400 응답을 그대로 인라인 에러로
표시하는 것으로 충분하다.

### D-W15-005: 확정된 API 계약 (추측 금지)
- **매도 등록**: `POST /sales` — `{allocationId, quantity, price?, amount?, saleDate, fee?, tax?, otherCost?}` → 201 / 400(누적매도수량>배정수량 등) / 404 / 409
- **비용 등록**: `POST /costs` — `{subscriptionId, costType, amount, incurredAt, memo?}` → 201 / 400 / 404
- **수익 조회(단건)**: `GET /sales/{saleId}/profit` → 200 / 404
- **수익 조회(투자자별 목록+합계)**: `GET /investors/{investorId}/profits` → 200 / 404
- **매도/배정 참고 정보**: `GET /investment-history/{subscriptionId}` (Epic 11) — `allocation.allocatedQuantity`, `sales[]`, `remainingQuantity` 참고 표시용

### D-W15-006: 화면 구성 — 매도 등록 / 비용 등록 / 수익 확인 3개 화면
"매도 목록"은 별도 화면을 새로 만들지 않는다. Epic 11의 투자 내역 화면
(`GET /investment-history`)이 이미 Subscription 기준으로 Allocation과
`sales[]`를 중첩 표시하므로, 매도 이력 확인은 그 화면을 재사용한다.
이 Epic은 **입력(매도/비용 등록)과 수익 확인(계산 결과 조회)**에만
집중한다.

이 결정들은 `docs/spec/web-sale-profit-workflow.md` /
`docs/architecture/web-sale-profit-workflow.md` 작성 태스크 지시에
반영되었다.
