# 웹 IPO·청약·배정 입력 — 세부 기능 분해 (web-subscription-workflow breakdown)

> **Epic**: Epic 14 (웹 IPO·청약·배정 입력)
> **분야**: 프론트엔드 (React + TypeScript, FSD, TanStack Query) — frontend 워크스페이스
> **작성일**: 2026-09-21
> **API 계약 출처**: docs/spec/web-subscription-workflow.md (D-W14-001~006, D-W14-003 확정), docs/architecture/web-subscription-workflow.md
> **의존 관계**: Epic 13(web-account-setup)의 `shared`/`entities` 재사용. 백엔드는 Epic 2/4/5/6 완료, **Epic 7(POST /allocations)·Epic 11(GET /investment-history) 미구현**

---

## 세부 기능(SF) 개요

총 **3개 SF**로 분해한다. 각 SF는 화면 하나 단위이며, PR 하나로 리뷰 가능한 크기다. 순차 의존 관계이며 한 번에 하나씩만 `ready`가 되도록 체인을 건다.

```
SF-1 (청약 등록 화면) → SF-2 (청약 목록 화면) → SF-3 (배정 등록 화면)
```

| SF | 화면 | 선행 조건 | 백엔드 API 상태 | 착수 가능 여부 |
|---|---|---|---|---|
| SF-1 | 청약 등록 화면 | Epic 13 완료(우선순위는 프론트 워크스페이스 순서상 자연 충족) | GET /investors, GET /securities-accounts, GET /ipos, POST /subscriptions **전부 구현 완료** | **즉시 착수 가능** |
| SF-2 | 청약 목록 화면 | SF-1 + **Epic 11 백엔드(GET /investment-history) 필요** | GET /investment-history **미구현** | UI 구현은 가능하나 실연동·E2E는 Epic 11 완료 후 |
| SF-3 | 배정 등록 화면 | SF-2 + **Epic 7 백엔드(POST /allocations) 구현 완료** + Epic 11 상세 조회 | POST /allocations, GET /investment-history/{id} **미구현** | Epic 7·11 백엔드 완료 후 |

---

## SF-1: 청약 등록 화면

**범위**: `/subscriptions/register` 청약 등록 화면을 구현한다. 투자자/증권계좌/공모주 드롭다운, 수량·증거금·청약일시·메모 입력, `POST /subscriptions` 제출, 성공 시 청약 목록 이동, 400/404/409/네트워크 에러의 필드별 인라인 매핑을 포함한다. 이 SF에서 Epic 14 전용 `entities`(`ipo`, `subscription`)와 공통 `ConfirmDialog`를 신규 생성한다.

**커버하는 AC**: AC-001(등록 성공), AC-002(0 이하 수량), AC-003(음수 증거금), AC-004(404), AC-005(중복 409), AC-006(서버 유효성 400), AC-007(성공 후 목록 이동), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 4/6/7/12/13은 이 SF 대상.

**선행 SF**: 없음 (첫 번째 SF). Epic 13이 `shared`(client, validators, Button, Input, FormField, ErrorMessage, Spinner)와 `entities/investor`를 이미 제공한다.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `entities/ipo/types.ts`, `entities/ipo/api-client.ts` — **신규**. `Ipo` 타입 정의, `GET /ipos` 호출(`fetchIpos`)
- `entities/securities-account/types.ts`, `entities/securities-account/api-client.ts` — **신규 생성**(현재 프론트엔드에 이 엔티티가 없음). `SecuritiesAccount` 타입 정의, `GET /securities-accounts` 호출
- `entities/investor/api-client.ts` — **기존에 `registerInvestor`(POST)만 존재**하므로 `GET /investors` 목록 조회 함수(`fetchInvestors`)를 추가
- `entities/subscription/types.ts` — `Subscription` 타입 정의 (등록 요청/응답)
- `entities/subscription/api-client.ts` — `POST /subscriptions` 호출(`createSubscription`)
- `features/subscription-register/SubscriptionRegisterForm/SubscriptionRegisterForm.tsx` (+ `.module.css`) — 청약 등록 폼 (react-hook-form, 드롭다운 3종 + 수량/증거금/청약일시/메모)
- `features/subscription-register/useSubscriptionRegister.ts` — `POST /subscriptions` `useMutation` 훅 (onSuccess: `invalidateQueries(['investmentHistory'])` → `/subscriptions` 이동)
- `pages/SubscriptionRegisterPage/SubscriptionRegisterPage.tsx` (+ `.module.css`) — 화면 배치
- `App.tsx` — `/subscriptions/register` 라우트 추가
- `shared/components/ConfirmDialog/ConfirmDialog.tsx` (+ `.module.css`) — **신규**, 공통 확인 다이얼로그 (props: isOpen, title, message, onConfirm, onCancel, aria-modal, Escape 닫기, 포커스 트랩)

**사용 백엔드 API (전부 구현 확인)**:
- `GET /investors` — Epic 2, InvestorController `@GetMapping` (main 머지됨)
- `GET /securities-accounts` — Epic 4, SecuritiesAccountController `@GetMapping` (main 머지됨)
- `GET /ipos` — Epic 5, IpoController `@GetMapping` (main 머지됨)
- `POST /subscriptions` — Epic 6 SF-2, PR #104로 main 머지됨 (`0c4dd04`)

**특이사항**:
- 폼 유효성은 서버 규칙을 대체하지 않는다: quantity는 0 초과 정수, depositAmount는 0 이상 정수만 프론트 검증(D-W14-002). 최소 청약 단위 등 백엔드에 없는 규칙 신설 금지.
- 404는 어느 엔티티가 없는지에 따라 필드별로 매핑합니다(투자자/증권계좌/공모주 각각 "~을/를 찾을 수 없습니다.").
- `ConfirmDialog`는 이 Epic 스코프에 수정/삭제 화면이 없어 **컴포넌트만 공통으로 생성**한다(AC-017 대비, 사용처 검증은 수정/삭제 기능 도입 시). AC-018(수정 이력 UI 없음)은 이 Epic에 수정 이력 화면이 원천적으로 없어 충족된다.
- `depositAmount`가 0이어도 유효한 값(증거금 0원 허용)이므로 "0 이상" 검증이며 "0 초과"가 아니다(E2E 시나리오 13).

---

## SF-2: 청약 목록 화면

**범위**: `/subscriptions` 청약 목록 화면을 구현한다. `GET /investment-history?investorId=...` 조회, 미배정/배정완료/전체 필터, 청약 카드 목록(공모명·증권계좌·청약수량·청약일시·배정 여부), 미배정(`allocation: null`) 카드에만 "배정 입력" 버튼 표시 → `/allocations/register?subscriptionId={id}` 이동을 포함한다.

**커버하는 AC**: AC-008(목록 로딩), AC-009(미배정 필터), AC-010(배정완료 필터), AC-011(미배정 카드에 배정 입력 버튼), AC-012(버튼 클릭 시 배정 등록 이동), AC-019(접근성), AC-020(반응형). E2E 시나리오 3(필터 전환)이 이 SF 대상.

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 11(investment-query-api)의 `GET /investment-history` 구현 필요 — 현재 Phase 0만 완료된 상태로 미구현.**

> ⚠️ **중요한 의존성 발견**: 아키텍처 문서(64, 136, 154행)와 spec 2절은 청약 목록 화면이 **Epic 6의 `GET /subscriptions`가 아니라 Epic 11의 `GET /investment-history`** 를 사용한다고 명시한다(배정 여부 판단에 `allocation` 필드가 필요하기 때문). Epic 11은 로드맵 11번째 백엔드 Epic이며 아직 SF 분해 전이다. 따라서 SF-2 화면 자체는 확정 계약(D-W14-003)으로 구현 가능하지만, 실데이터 연동과 E2E 시나리오 1(목록 로드) 검증은 Epic 11 백엔드가 머지된 뒤에 가능하다.

**산출물 파일 경로**:
- `entities/subscription/api-client.ts` — `getInvestmentHistory(investorId, filters)` 추가 (`GET /investment-history`)
- `entities/subscription/types.ts` — `InvestmentHistoryItem` 타입 (allocation 필드 nullable, subscriptionQuantity 필드 — architecture 209행에서 `quantity`가 아닌 `subscriptionQuantity`로 정정 확인)
- `features/subscription-list/SubscriptionList/SubscriptionList.tsx` (+ `.module.css`) — 카드 목록
- `features/subscription-list/SubscriptionList/SubscriptionCard.tsx` — 개별 청약 카드 (배정 여부 배지, 미배정 시 "배정 입력" 버튼)
- `features/subscription-list/SubscriptionFilter.tsx` — `useState<'all'|'unallocated'|'allocated'>` 로컬 필터(URL 파라미터 미사용, architecture 176~188행)
- `features/subscription-list/useInvestmentHistory.ts` — `useQuery`, queryKey `['investmentHistory', { investorId, ...filters }]`
- `pages/SubscriptionListPage/SubscriptionListPage.tsx` (+ `.module.css`)
- `App.tsx` — `/subscriptions` 라우트 추가

**사용 백엔드 API**:
- `GET /investment-history?investorId=...` — **Epic 11, 미구현** (init 정렬·페이지네이션 계약은 docs/spec/investment-query-api.md 및 D-Q-001~006 참고)

**특이사항**:
- 필터 상태는 URL 파라미터가 아닌 로컬 state로 관리한다(뒤로가기/앞으로가기로 필터가 바뀌지 않게. architecture 176~188행).
- "배정 입력" 버튼은 `allocation: null`인 카드에만 표시하고, 이동은 `/allocations/register?subscriptionId={id}` URL 파라미터로 전달한다.
- 캐시 무효화 방향: `invalidateQueries({ queryKey: ['investmentHistory'] })`만 사용(청약 등록/배정 등록 성공 시 이 키 전체 무효화).

---

## SF-3: 배정 등록 화면

**범위**: `/allocations/register?subscriptionId={id}` 배정 등록 화면을 구현한다. URL 파라미터로 청약 상세를 조회해 청약수량을 읽기 전용 `ReferenceQuantity`로 참고 표시, 배정수량·배정일시·배정방식·배정단가·비고 입력, `POST /allocations` 제출, 성공 시 청약 목록 이동(해당 청약이 "배정완료"로 갱신됨), 400(배정수량>청약수량 서버 메시지 그대로 표시)/404/409/네트워크 에러의 인라인 매핑을 포함한다.

**커버하는 AC**: AC-013(배정 등록 성공), AC-014(배정수량>청약수량 400), AC-015(404), AC-016(중복 409), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류), AC-022(참고 표시 — 청약수량 읽기 전용), AC-023(성공 후 목록 "배정완료" 갱신). E2E 시나리오 2(참고 표시)/5(배정수량 초과)가 이 SF 대상.

**선행 SF**: SF-2 (필수, 버튼 진입점). **선행 조건: Epic 7 백엔드(POST /allocations) 구현 완료 + Epic 11의 GET /investment-history/{id} 구현 필요** — 둘 다 현재 미구현.

> task 요청 반영: 이 SF는 **"선행 조건: Epic 7 백엔드 구현 완료"** 를 명시한다. Epic 7(배정 결과 관리)은 docs/breakdown/allocation-management.md에 5개 SF 분해만 되어 있고 아직 구현 전이며, 참고 표시용 상세 조회는 Epic 11(미구현)의 `GET /investment-history/{subscriptionId}`를 사용한다.

**산출물 파일 경로**:
- `entities/allocation/types.ts`, `entities/allocation/api-client.ts` — **신규**. `Allocation` 타입 정의, `POST /allocations` 호출(`createAllocation`)
- `entities/subscription/api-client.ts` — `getSubscriptionDetail(subscriptionId)` 추가 (`GET /investment-history/{subscriptionId}`)
- `features/allocation-register/AllocationRegisterForm/AllocationRegisterForm.tsx` (+ `.module.css`) — 배정 등록 폼
- `features/allocation-register/AllocationRegisterForm/ReferenceQuantity.tsx` — 읽기 전용 청약수량 표시(라벨 "청약수량 (참고)", `aria-readonly`, readOnly+disabled)
- `features/allocation-register/useAllocationRegister.ts` — `POST /allocations` `useMutation` (onSuccess: `invalidateQueries(['investmentHistory'])` → `/subscriptions` 이동)
- `features/allocation-register/useSubscriptionDetail.ts` — `useQuery`, queryKey `['subscriptionDetail', subscriptionId]`, `enabled: !!subscriptionId`
- `pages/AllocationRegisterPage/AllocationRegisterPage.tsx` (+ `.module.css`) — `useSearchParams`로 subscriptionId 추출, 없으면 `/subscriptions` 리다이렉트
- `App.tsx` — `/allocations/register` 라우트 추가

**사용 백엔드 API (전부 미구현)**:
- `POST /allocations` — Epic 7, docs/breakdown/allocation-management.md SF-2 대상
- `GET /investment-history/{subscriptionId}` — Epic 11, 미구현 (참고 표시용; 응답에서 `subscriptionQuantity` 필드 추출 — architecture 209행 정정 반영)

**특이사항**:
- 배정수량>청약수량 최종 검증은 **서버가 수행**한다(AC-014). 프론트는 UX 보조(경고 스타일)만 제공하고 서버 검증을 재구현하지 않는다. 서버 400 메시지는 `allocatedQuantity` 필드 아래 그대로 표시.
- `AllocationRegisterPage`에서 `subscriptionId`가 유효하지 않으면(404) 청약 목록으로 리다이렉트(architecture 리스크 표 참고).
- 배정 등록 성공 후에는 목록 쿼리를 무효화해 같은 턴에 "배정완료" 배지로 갱신되도록 한다.

---

## E2E 테스트 매핑 요약

| E2E 시나리오 | 내용 | 대응 SF |
|---|---|---|
| 1 | 청약 등록 → 목록 미배정 확인 → 배정 등록 → 배정완료 (전체 플로우) | SF-1 + SF-2 + SF-3 (Epic 7·11 백엔드 머지 후 검증 가능) |
| 2 | 배정 등록 시 참고 표시 | SF-3 |
| 3 | 필터 전환 (전체→미배정→배정완료) | SF-2 |
| 4 | 청약 등록 실패 (중복 409) | SF-1 |
| 5 | 배정 등록 실패 (배정수량>청약수량) | SF-3 |
| 6 | 청약 등록 실패 (404) | SF-1 |
| 7 | 네트워크 오류 | SF-1, SF-3 |
| 8, 9 | 수정/삭제 확인 다이얼로그 | ConfirmDialog 컴포넌트 생성(SF-1) — 수정/삭제 화면 자체는 후속 Epic에서 검증 |
| 10 | axe-core 접근성 | SF-1~3 각 화면 |
| 11 | 640/768/1024px 반응형 | SF-1~3 각 화면 |
| 12 | 최소 청약 단위 미적용 | SF-1 |
| 13 | 수량/증거금 유효성 (0 및 음수) | SF-1 |

---

## 구현 순서 가이드

1. **SF-1(청약 등록 화면)은 즉시 착수 가능하다** — `/subscriptions/register`에 필요한 백엔드 4개 API(GET /investors, GET /securities-accounts, GET /ipos, POST /subscriptions)가 전부 main에 머지되어 있다(PR #104 포함).
2. **SF-2(청약 목록 화면)는 화면 구현을 미리 진행하되** `GET /investment-history`(Epic 11)는 미구현이므로, 실데이터 연동·E2E 시나리오 1 검증은 **Epic 11 백엔드 머지를 선행 조건으로** 둔다.
3. **SF-3(배정 등록 화면)은 Epic 7(POST /allocations)과 Epic 11(GET /investment-history/{id}) 백엔드 구현·머지 후 착수**를 기본으로 한다. Epic 7은 docs/breakdown/allocation-management.md를 따라 백엔드 워크스페이스에서 병렬 진행 중인 6과 이어서 진행한다.