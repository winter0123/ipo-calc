# 작업 로그: 웹 IPO·청약·배정 입력 (web-subscription-workflow, Epic 14)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 최신화)

`docs/research/web-subscription-workflow.md`는 이전 세션에서 이미 실시간
거래 처리 스코프 오류를 정정한 상태(사후 기록 서비스 전제 명시)라 큰
문제는 없으나, "불확실한 점"으로 남긴 항목들을 확정된 백엔드 spec(Epic 6/7)
기준으로 결정한다.

### D-W14-001: 청약 등록과 배정 등록은 별도 화면으로 분리한다
Subscription(Epic 6)과 Allocation(Epic 7)은 별도의 백엔드 리소스이며
(`POST /subscriptions`, `POST /allocations`), 실제로 사용자는 청약
시점과 배정 발표 시점에 시간차를 두고 각각 기록한다(배정은 청약 마감
후 며칠 뒤 발표됨). 두 화면을 하나로 합치지 않는다. 배정 등록 화면은
"아직 배정 미입력인 청약 목록"(Epic 11 `GET /investment-history`에서
`allocation: null`인 항목)에서 특정 청약을 선택해 진입하는 방식으로
연결한다.

### D-W14-002: "최소 청약 단위" 등 증권사별 규칙은 프론트에서 강제하지 않는다
[[subscription-management]] spec에는 `quantity`가 "0 초과 정수"로만
검증되며, 최소 청약 단위 배수 같은 규칙은 존재하지 않는다. 조사 문서가
언급한 "최소 청약 단위 배수 적용"은 실제 백엔드 spec에 없는 항목이므로
프론트는 이를 강제 검증하지 않는다 — 단순히 0 초과 정수만 검증한다.

### D-W14-003: 확정된 API 계약 (추측 금지)
- **청약 등록**: `POST /subscriptions` — `{investorId, securitiesAccountId, ipoId, quantity, depositAmount, subscribedAt, memo?}` → 201 / 400 / 404 / 409(중복)
- **청약 목록(미배정 필터용)**: `GET /investment-history?investorId=...` — Epic 11, `allocation` 필드로 미배정 여부 판단
- **배정 등록**: `POST /allocations` — `{subscriptionId, allocatedQuantity, allocatedAt, allocationMethod?, allocationPrice?, note?}` → 201 / 400(배정수량>청약수량 등) / 404 / 409(중복)
- **청약/배정 수정**: `PATCH /subscriptions/{id}`, `PATCH /allocations/{id}`
- **청약/배정 취소**: `DELETE /subscriptions/{id}`, `DELETE /allocations/{id}` (논리 삭제)
- **투자자/증권계좌/공모주 목록**(드롭다운용): `GET /investors`, `GET /securities-accounts`, `GET /ipos`

### D-W14-004: 배정수량 초과 검증은 서버가 최종 판단, 프론트는 UX 보조만
Σ(ACTIVE 매도수량) ≤ 배정수량, 배정수량 ≤ 청약수량 검증은 이미 Epic 7
백엔드에서 수행한다(AC-003). 프론트는 사용자 경험 개선을 위해 폼에
청약수량을 표시해 참고하게 하는 정도의 보조 검증만 추가하고, 최종
검증 실패 시 서버 400 응답 메시지를 그대로 인라인 에러로 표시한다 —
클라이언트에서 서버 검증 로직을 재구현하지 않는다.

### D-W14-005: 수정 이력 UI는 만들지 않는다
[[sale-management]] D-SL-005 / [[cost-recording]] D-C-003과 동일 원칙 —
백엔드에 별도 변경 이력 테이블이 없으므로 프론트에도 "수정 이력 보기"
같은 화면을 만들지 않는다. 단순 수정 폼(현재 값을 불러와 덮어쓰기)만
제공한다. 수정/삭제 전 확인 다이얼로그(조사 결론)는 유지한다.

### D-W14-006: 관리자 화면은 이 프로젝트 범위 밖
[[account-authentication]] 확정 사항대로 MVP는 단일 계정 타입(개인
사용자)만 존재하며 관리자 기능은 없다. "관리자를 통한 예외적 기록
수정"은 이 Epic에서 설계하지 않는다.

이 결정들은 `docs/spec/web-subscription-workflow.md` /
`docs/architecture/web-subscription-workflow.md` 작성 태스크 지시에
반영되었다.

## 2026-09-21 — SF 분해 (breakdown) (티켓: task_ca43430dea7a)

`docs/breakdown/web-subscription-workflow.md` 작성 완료. 총 **3개 SF**로 분해한다.

- **SF-1 청약 등록 화면** — 즉시 착수 가능. 필요한 백엔드 4개 API(GET /investors, GET /securities-accounts, GET /ipos, POST /subscriptions)가 전부 main 머지 확인됨(POST /subscriptions은 PR #104, commit 0c4dd04). 이 SF에서 `entities/ipo`, `entities/subscription`, `entities/securities-account`(프론트에 아직 없음)와 `shared ConfirmDialog`를 신규 생성.
- **SF-2 청약 목록 화면** — 화면 구현은 가능하나 **Epic 11(`GET /investment-history`)이 미구현**이어서 실연동·E2E 검증은 Epic 11 백엔드 머지 후. (아키텍처 문서가 "Epic 11에서 이미 구현된 API를 재사용"이라 전제했으나 실제로는 Phase 0만 완료됨 — 발견사항으로 문서에 기록)
- **SF-3 배정 등록 화면** — **선행 조건: Epic 7(`POST /allocations`) 백엔드 구현 완료 + Epic 11 상세(`GET /investment-history/{id}`) 구현**. 둘 다 미구현.

검증: 백엔드 워크트리(backend-3)에서 컨트롤러 존재 여부(GET /investors, /securities-accounts, /ipos, POST /subscriptions + main log)와 Epic 11/7 미구현을 실제 파일로 확인. 프론트 FSD(entities/features/pages) 및 Epic 13 SF-4 패턴(react-hook-form + TanStack Query + 셀렉트 드롭다운)도 읽고 반영함.

커밋/PR은 Master 담당(워커는 git 작업 안 함).
