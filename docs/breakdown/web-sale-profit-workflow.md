# 웹 매도·비용·수익 확인 — 세부 기능 분해 (web-sale-profit-workflow breakdown)

> **Epic**: Epic 15 (웹 매도·비용·수익 확인)
> **분야**: 프론트엔드 (React + TypeScript, FSD, TanStack Query) — frontend 워크스페이스
> **작성일**: 2026-09-22
> **API 계약 출처**: docs/spec/web-sale-profit-workflow.md (D-W15-005), docs/architecture/web-sale-profit-workflow.md
> **의존 관계**: Epic 13/14의 `shared`/`entities` 재사용. 백엔드는 Epic 8 SF-5(취소) 병합 대기 중, Epic 9/10/11 미구현

## 드리프트 재점검(2026-09-28)

| 항목 | 변경 전 | 변경 후 | 근거 |
|---|---|---|---|
| SF-2 백엔드 상태 | POST /costs **미구현** | Epic 9, 구현·병합 완료 | current-status.md: Epic 9 SF-2~5 전체 완료(PR #146~#151) |
| SF-3 GET /sales/{saleId}/profit | **미구현** | Epic 10 SF-2, 구현·병합 완료 | current-status.md: PR #155 PASS |
| SF-3 GET /investors/{investorId}/profits | **미구현**(Epic 11로 표기) | 미구현(Epic 10 SF-3 진행 중) | roadmap.md Epic 10 SF-3, current-status.md: SF-3 미착수 |
| SF-3 선행 조건 | Epic 9·10 둘 다 미구현 | Epic 9 완료, Epic 10 SF-1~2 완료·SF-3 진행 중 | roadmap.md §5, current-status.md §5 |
| 백엔드 게이트 조건 | Epic 9·10 둘 다 미구현 | Epic 9 완료, Epic 10 SF-1~2 완료·SF-3 진행 중 | 위 변경 사항 반영 |

> 수정 근거: roadmap.md §4/§5 및 current-status.md의 PR 병합 이력을 대조하여 "미구현" 표기를 실제 상태로 최신화. 다른 문서·코드는 수정하지 않음.

---

## 세부 기능(SF) 개요

총 **3개 SF**로 분해한다. 각 SF는 화면 하나 단위이며, PR 하나로 리뷰 가능한 크기다. 순차 의존 관계이며 한 번에 하나씩만 `ready`가 되도록 체인을 건는다.

```
SF-1 (매도 등록 화면) → SF-2 (비용 등록 화면) → SF-3 (수익 확인 화면)
```

| SF | 화면 | 선행 조건 | 백엔드 API 상태 | 착수 가능 여부 |
|---|---|---|---|---|
| SF-1 | 매도 등록 화면 | Epic 8 SF-4 완료(자연 충족) | POST /sales **SF-5(취소) 병합 대기 중**, GET /investment-history/{subscriptionId}(Epic 11) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |
| SF-2 | 비용 등록 화면 | SF-1 + Epic 8 완료 | POST /costs **Epic 9, 구현·병합 완료** | 실연동·E2E 즉시 가능(Epic 9 완료됨) |
| SF-3 | 수익 확인 화면 | SF-2 + Epic 9/10 완료 | GET /sales/{saleId}/profit **Epic 10 SF-2, 구현·병합 완료**, GET /investors/{investorId}/profits **미구현(Epic 10 SF-3 진행 중)** | UI 구현은 가능하나 실연동·E2E는 GET /investors/{investorId}/profits 완료 후 |

---

## SF-1: 매도 등록 화면

**범위**: `/sales/register` 매도 등록 화면을 구현한다. 배정 선택 드롭다운, 수량·단가·금액·일시·수수료·세금·기타비용 입력, `POST /sales` 제출, 성공 시 투자 내역 화면(Epic 11)으로 이동, 400/404/409/네트워크 에러의 필드별 인라인 매핑을 포함한다. 배정의 `remainingQuantity`를 참고 표시로 보여준다. 이 SF에서 Epic 15 전용 `entities`(`sale`)와 공통 `ConfirmDialog`를 신규 생성한다.

**커버하는 AC**: AC-001(등록 성공), AC-002(0 이하 수량), AC-003(음수 금액 필드), AC-004(404), AC-005(409 충돌), AC-006(누적매도수량>배정수량), AC-007(서버 유효성 400), AC-008(성공 후 이동), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류), AC-018(참고 표시). E2E 시나리오 1/2/4/6/7/11/12/13/18/19/22는 이 SF 대상.

**선행 SF**: 없음 (첫 번째 SF). Epic 8이 `shared`(client, validators, Button, Input, FormField, ErrorMessage, Spinner)와 `entities/allocation`을 이미 제공한다.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `entities/sale/types.ts` — **신규**. `Sale` 타입 정의 (POST /sales 요청/응답)
- `entities/sale/api-client.ts` — **신규**. `POST /sales` 호출(`createSale`)
- `features/sale-register/SaleRegisterForm/SaleRegisterForm.tsx` (+ `.module.css`) — 매도 등록 폼 (react-hook-form, 배정 드롭다운 + 수량/단가/금액/일시/수수료·세금·기타비용)
- `features/sale-register/SaleRegisterForm/ReferenceQuantity.tsx` — **참고 표시**(선택된 배정의 `remainingQuantity` 읽기 전용 컴포넌트)
- `features/sale-register/useSaleRegister.ts` — `POST /sales` `useMutation` 훅 (onSuccess: `invalidateQueries(['investmentHistory'])` → 투자 내역 화면 이동)
- `features/sale-register/useAllocationOptions.ts` — `GET /investment-history/{subscriptionId}`로 배정 드롭다운 옵션 조회 훅
- `pages/SaleRegisterPage/SaleRegisterPage.tsx` (+ `.module.css`) — 화면 배치
- `App.tsx` — `/sales/register` 라우트 추가
- `shared/components/ConfirmDialog/ConfirmDialog.tsx` (+ `.module.css`) — **기존 재사용**(Epic 14에서 생성됨, 중복 생성 금지)

**사용 백엔드 API**:
- `POST /sales` — Epic 8, SF-5(취소) 병합 대기 중 (PR #139 이후)
- `GET /investment-history/{subscriptionId}` — Epic 11, **미구현** (참고 표시용; 응답에서 `remainingQuantity` 필드 추출)

**특이사항**:
- 폼 유효성은 서버 규칙을 대체하지 않는다: quantity는 0 초과 정수, price/amount/fee/tax/otherCost는 0 이상 정수만 프론트 검증(D-W15-002). 최소 매도 단위 등 백엔드에 없는 규칙 신설 금지.
- 404는 배정이 없는 경우에 `allocationId` 필드에 "배정을 찾을 수 없습니다."를 매핑한다.
- 409 충돌 시 서버 응답 메시지를 그대로 인라인 표시한다(D-W15-004 락 collision UI 없음).
- 400 응답(누적매도수량>배정수량 등) 시 서버 응답 메시지를 `quantity` 필드 아래 인라인 에로로 그대로 표시한다(D-W15-004).
- `ReferenceQuantity` 컴포넌트는 `<input type="number" value={remainingQuantity} readOnly disabled />` 또는 유사한 읽기 전용 UI로 표시한다. 라벨: "남은 수량 (참고)", `aria-readonly="true"` 속성 적용.
- GitHub Issue #134(`apiFetch`가 서버 400 메시지를 버리는 문제)에 대한 처리 방식은 별도 결정이 필요함(아래 "GitHub Issue #134 처리 방식" 참조).

---

## SF-2: 비용 등록 화면

**범위**: `/costs/register` 비용 등록 화면을 구현한다. 청약 선택 드롭다운, 비용종류·금액·발생일시·메모 입력, `POST /costs` 제출, 성공 시 비용 목록 화면으로 이동, 400/404/네트워크 에러의 필드별 인라인 매핑을 포함한다. 이 SF에서 Epic 15 전용 `entities`(`cost`)를 신규 생성한다.

**커버하는 AC**: AC-009(등록 성공), AC-010(0 이하 금액), AC-011(404), AC-012(서버 유효성 400), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 3/8/9/10/14/15는 이 SF 대상.

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 8(분할 매도 기록) 완료 필요** — 현재 SF-4까지 완료, SF-5(취소)만 남음.

**산출물 파일 경로**:
- `entities/cost/types.ts` — **신규**. `Cost` 타입 정의 (POST /costs 요청/응답)
- `entities/cost/api-client.ts` — **신규**. `POST /costs` 호출(`createCost`)
- `features/cost-register/CostRegisterForm/CostRegisterForm.tsx` (+ `.module.css`) — 비용 등록 폼 (react-hook-form, 청약 드롭다운 + 비용종류/금액/발생일시/메모)
- `features/cost-register/useCostRegister.ts` — `POST /costs` `useMutation` 훅 (onSuccess: 비용 목록 화면으로 이동, 해당 화면의 목록 쿼리 무효화)
- `features/cost-register/useSubscriptionOptions.ts` — `GET /investment-history`로 청약 드롭다운 옵션 조회 훅
- `pages/CostRegisterPage/CostRegisterPage.tsx` (+ `.module.css`) — 화면 배치
- `App.tsx` — `/costs/register` 라우트 추가

**사용 백엔드 API**:
- `POST /costs` — Epic 9, 구현·병합 완료 (docs/breakdown/cost-recording.md 참고)

**특이사항**:
- 폼 유효성은 서버 규칙을 대체하지 않는다: amount는 0 초과 정수만 프론트 검증(D-W15-002).
- 404는 청약이 없는 경우에 `subscriptionId` 필드에 "청약을 찾을 수 없습니다."를 매핑한다.
- 400 응답 시 서버 응답 메시지를 해당 필드 아래 인라인 에로로 그대로 표시한다.
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## SF-3: 수익 확인 화면

**범위**: `/profits/view` 수익 확인 화면을 구현한다. 매도건별/투자자별 두 조회 모드를 탭 또는 토글로 전환하며 하나의 화면에 배치한다. `GET /sales/{saleId}/profit` 및 `GET /investors/{investorId}/profits` 호출, 수익 결과 테이블 표시, 로딩/오류/빈 상태를 포함한다. 수익이 음수(손실)인 경우에도 동일한 필드를 표시하며, 금액에 마이너스 부호를 적용한다. 이 SF에서 Epic 15 전용 `entities`(`profit`)를 신규 생성한다.

**커버하는 AC**: AC-013(매도건별 수익 조회 성공), AC-014(투자자별 수익 조회 성공), AC-015(존재하지 않는 매도건 404), AC-016(존재하지 않는 투자자 404), AC-017(수익 조회에서 음수 손실 표시), AC-019(접근성), AC-020(반응형), AC-022(확인 다이얼로그 재사용). E2E 시나리오 5/16/17/20/21은 이 SF 대상.

**선행 SF**: SF-2 (필수). **백엔드 선행 조건: Epic 9(비용 기록) 완료됨, Epic 10(수익·수익률 계산) SF-1~2 완료·SF-3 진행 중** — SF-3 전체 완료(GET /investors/{investorId}/profits 포함)까지는 SF-3의 완전한 실연동이 불가하다.

**산출물 파일 경로**:
- `entities/profit/types.ts` — **신규**. `SaleProfit`, `InvestorProfit` 타입 정의 (수익 조회 API 응답)
- `entities/profit/api-client.ts` — **신규**. `GET /sales/{saleId}/profit`, `GET /investors/{investorId}/profits` 호출
- `features/profit-view/ProfitTable/ProfitTable.tsx` (+ `.module.css`) — 수익 조회 결과 표시 테이블 컴포넌트
- `features/profit-view/ProfitTable/ProfitRow.tsx` — 단건 수익 행 컴포넌트
- `features/profit-view/ProfitTable/ProfitSummaryRow.tsx` — 합계 행 컴포넌트(투자자별 조회)
- `features/profit-view/ProfitQueryToggle.tsx` — 매도건별/투자자별 전환 토글 컴포넌트
- `features/profit-view/useSaleProfit.ts` — `GET /sales/{saleId}/profit`를 조회하는 `useQuery` 훅
- `features/profit-view/useInvestorProfits.ts` — `GET /investors/{investorId}/profits`를 조회하는 `useQuery` 훅
- `pages/ProfitViewPage/ProfitViewPage.tsx` (+ `.module.css`) — 화면 배치
- `App.tsx` — `/profits/view` 라우트 추가

**사용 백엔드 API**:
- `GET /sales/{saleId}/profit` — Epic 10 SF-2, 구현·병합 완료 (PR #155, docs/breakdown/profit-calculation.md 참고)
- `GET /investors/{investorId}/profits` — Epic 10 SF-3, 미구현(진행 중) (docs/breakdown/profit-calculation.md 참고)

**특이사항**:
- 수익이 음수(손실)인 경우에도 API 응답 필드 값을 그대로 표시하며, `preTaxProfit` 및 `afterTaxProfit` 필드에 빨간색 또는 마이너스 기호를 적용한다(D-W15-001 CalculationTrace 금지).
- `ProfitQueryToggle` 컴포넌트가 `useState<'sale' | 'investor'>`로 현재 모드를 관리한다.
- 모드 전환 시 현재 조회 결과를 초기화하고, 새 모드의 입력 필드를 리셋한다.
- `useSaleProfit`과 `useInvestorProfits`를 각각 독립적으로 사용하며, 현재 활성 모드에 해당하는 훅만 `enabled: true`로 설정한다.
- 탭 전환 시 `invalidateQueries`를 통해 상대방 모드의 캐시를 무효화할 필요는 없다. 두 쿼리의 `queryKey`가 다르므로 독립적으로 캐시가 관리된다.
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## GitHub Issue #134 처리 방식

**문제**: `apiFetch`가 서버 에러 응답 본문을 버려서 여러 Register 폀이 400 상세 메시지를 못 보여줌(Issue #134)

**영향 범위**: Epic 15의 모든 폼 SF(SF-1, SF-2, SF-3)에 영향

**결정 필요 사항**: 이 Epic의 별도 선행 SF로 처리할지, 각 폼 SF에 포함시킬지, 아니면 범위 밖으로 미룰지 결론을 내리고 근거를 문서에 남긴다.

**검토 사항**:
1. 별도 선행 SF로 처리: `shared/api/client.ts`의 `apiFetch` 수정 및 4개 폼의 에러 매칭 로직 동시 수정
   - 장점: 한 번의 수정으로 모든 폼에 적용 가능, 일관된 에러 처리 보장
   - 단점: 선행 SF 추가로 인해 전체 일정 지연, 다른 Epic과의 의존성 증가
2. 각 폼 SF에 포함: 각 SF에서 관련 파일들을 함께 수정
   - 장점: 선행 SF 추가 없이 점진적 개선 가능
   - 단점: 중복 작업 발생, 일관성 유지 어려움
3. 범위 밖으로 미룰: 현재 상태 유지하고 후속 Epic에서 처리
   - 장점: 현재 Epic 집중도 유지
   - 단점: 사용자 경험 저하 지속, 기술 부채 누적

**권고안**: 별도 선행 SF로 처리하는 것이 적절하다.
- 이유: Issue #134은 Epic 2, 4, 14의 이미 merge된 코드에 영향을 주는 공통 버그이므로, 별도 SF로 정리하여 기술 부채를 해결하는 것이 장기적으로 효율적이다.
- 선행 SF 명칭: `SF-0: 공통 apiFetch 에러 처리 수정`
- 선행 조건: 없음 (첫 번째 SF)
- 산출물: 
  - `ui/src/shared/api/client.ts` — `apiFetch` 수정으로 4xx/5xx 응답의 JSON body에서 메시지 필드 읽기
  - `features/*/*/*Form/*Form.tsx`의 `getFormErrorMessage()` 함수들 — 실제 서버 메시지를 표시하도록 수정

**현재 상태**: 사용자 결정이 필요하므로 breakdown 문서에 명시하고, 사용자 결정 없이 구현을 시작하면 안 된다.

---

## E2E 테스트 매핑 요약

| E2E 시나리오 | 내용 | 대응 SF |
|---|---|---|
| 1 | 배정 → 매도 등록 → 비용 등록 → 수익 확인 전체 플로우 | SF-1 + SF-2 + SF-3 (Epic 8/9/10/11 백엔드 머지 후 검증 가능) |
| 2 | 매도 등록 시 참고 표시 | SF-1 |
| 3 | 비용 등록 화면 진입 및 필수 필드 검증 | SF-2 |
| 4 | 매도 등록 실패 (누적매도수량>배정수량) | SF-1 |
| 5 | 매도 등록 실패 (409 락 충돌) | SF-1 |
| 6 | 매도 등록 실패 (404 배정 미존재) | SF-1 |
| 7 | 비용 등록 실패 (404 청약 미존재) | SF-2 |
| 8 | 비용 등록 실패 (서버 유효성 오류) | SF-2 |
| 9 | 수익 조회 실패 (매도건 미존재) | SF-3 |
| 10 | 수익 조회 실패 (투자자 미존재) | SF-3 |
| 11 | 수익 조회에서 음수 손실 표시 | SF-3 |
| 12 | 네트워크 오류 | SF-1, SF-2, SF-3 |
| 13 | 매도 등록 성공 후 투자 내역 화면 이동 | SF-1 |
| 14 | 비용 등록 성공 후 비용 목록 화면 이동 | SF-2 |
| 15 | axe-core 접근성 | SF-1~3 각 화면 |
| 16 | 640/768/1024px 반응형 | SF-1~3 각 화면 |
| 17 | 수정/삭제 확인 다이얼로그 | ConfirmDialog 컴포넌트 재사용(SF-1) |
| 18 | 매도 등록 시 참고 표시(remainingQuantity) | SF-1 |
| 19 | 매도 순서 (saleDate 기준 정렬) | Epic 11 투자 내역 화면에서 검증 |
| 20 | 매도 취소 API 연동 | SF-1 (Epic 8 SF-5 완료 후) |
| 21 | 수익 조회 캐시 무효화 | SF-3 (매도/비용 등록 후 수익 쿼리 무효화) |
| 22 | 매도 등록 필드 유효성 (0 이하, 음수) | SF-1 |

---

## 구현 순서 가이드

1. **GitHub Issue #134 처리 방식에 대한 사용자 결정 대기** — breakdown 문서의 이 섹션이 사용자 결정 사항임을 명시하고, 결정이 내려진 뒤에 계획 수립
2. **사용자 결정이 "별도 선행 SF"인 경우**: 
   - SF-0: 공통 apiFetch 에러 처리 수정 → SF-1 → SF-2 → SF-3 순서로 착수
   - SF-0은 API 계약과 무관하므로 언제든지 착수 가능
3. **사용자 결정이 "각 폼 SF에 포함"인 경우**:
   - SF-1 → SF-2 → SF-3 순서로 착수, 각 SF에서 관련 파일들을 함께 수정
4. **사용자 결정이 "범위 밖으로 미룰"인 경우**:
   - SF-1 → SF-2 → SF-3 순서로 착수, Issue #134는 후속 Epic에서 처리

**백엔드 게이트 조건**:
- SF-1(ui): Epic 8 SF-5(취소) 병합 대기 중 → 실제 작업은 가능하나 완전한 E2E 검증은 SF-5 완료 후
- SF-2(ui): Epic 8 완료 필요 → 현재 SF-4까지 완료, 백엔드 POST /costs는 Epic 9 완료로 실연동 가능
- SF-3(ui): Epic 9 완료, Epic 10 SF-1~2 완료·SF-3 진행 중 → GET /investors/{investorId}/profits 완료 후 실연동 가능

**planning 역할**: 
- Epic 15 breakdown 작성 완료 후 즉시 Epic 16 breakdown 시작 가능
- Epic 8이 병합되면 Epic 9와 Epic 11은 서로 독립이므로 별도 backend worktree에서 병렬 착수 가능
- frontend는 Epic 15 breakdown 승인 후 화면 구조·mock·테스트 계획을 시작하되, 실제 API 연동은 E8/E9/E10 완료 뒤 진행