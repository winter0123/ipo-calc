# 대시보드 결과 조합 작업 기록

## 2026-10-26 — 구현 시작

- `DashboardPage`의 탭 조회/loading/error 상태와 결과 렌더링이 한 컴포넌트에 결합된 것을 확인했다.
- 탭 결과 변환·출력만 page slice sibling으로 옮기고 query/state owner를 유지하는 범위로 진행한다.
- 사용자 요청에 따라 현재 리팩토링 단계가 끝나기 전까지 검증은 `NOT_RUN`이다.

## 구현 내용

- `DashboardTabContent`가 각 탭의 결과·빈 상태를 렌더링하고, page는 query/loading/error 및 기간/탭 상태를 계속 소유한다.
- 결과 전용 CSS를 sibling module로 이동했다. widget과 API 계약은 변경하지 않았다.
- 매도 없는 투자자의 표시, 3명 이하 펼침/4명 이상 접힘, 빈 상태 reset 전달을 확인하는 컴포넌트 테스트를 추가했다.
- `DashboardPage` 통합 흐름과 새 결과 컴포넌트 테스트가 UI 전체 suite에 포함된다.

## 검증

- UI 91 test files / 712 tests PASS.
- lint, typecheck, format:check, production build PASS.
- `git diff --check` PASS.
- 브라우저 수동 확인은 `NOT_RUN`이다.

## 다음 단계 — 매도 금액 검증 중복 정리

- `SaleService` 등록·수정 검증이 price/amount/fee/tax/otherCost 각각의 0 이상 규칙을 같은 오류 문구로 중복 구현하고 있음을 확인했다. 서비스 범위 안 private helper로 정책을 한 번만 정의했다.
- 필수값, 수량 0 초과, 상한, 누적 매도수량, 소유권 및 API 계약은 수정하지 않았다.
- backend 테스트·Checkstyle·Spotless·build는 아직 실행하지 않았다(NOT_RUN). 누적 backend 리팩토링 묶음이 정리된 뒤 한 차례 수행한다.

## 후속 — 청약 일정 집계 그룹핑 비용

- IPO별 기록을 묶을 때 항목마다 기존 배열 전체를 복사하던 코드를 mutable group append로 바꿨다. 전체 기록 수에 따라 누적 복사가 증가하던 경로를 단일 순회로 만들고, 기록 순서와 계좌별 건수는 유지한다.
- 복수 청약 기록이 모두 입력 순서대로 유지되는 테스트를 추가했다. UI 전체 검증은 아직 `NOT_RUN`이다.
