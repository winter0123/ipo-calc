# account-presence 프론트엔드 (SF-2) 작업 로그

## 2026-11-09 완료 (1차) / 2026-11-09 보완 (2차)
- 작업자: frontend 워커 (OpenCode/OpenRouter 무료 모델, depth 1)
- 목적: spec/architecture/breakdown/account-presence.md SF-2 구현 (핵심 체크박스 AC 포함)
- 범위: entities/securities-account/**, features/securities-manage/**, features/investment-history/AccountSelector/**, features/broker-connect/**, features/broker-sync/**(번호 미입력 안내만), 관련 테스트, work-log, user-guides manual 계좌등록 문구
- API 계약: spec/architecture/breakdown 문서 기준 (이미 확정, 백엔드와 병렬)
- 커밋/push/배포 금지, git diff --check PASS

## 완료된 변경 파일 (1차: 17 수정 + 2 신규 / 2차 보완: SecuritiesPage key 추가, 중복 회사명 표시 제거, 메시지 변경, api-client 단순화)
1. ui/src/entities/securities-account/types.ts — accountNumber nullable, register 요청 optional, 수정 요청 타입 추가
2. ui/src/entities/securities-account/api-client.ts — register/accountNumber 빈값 omit, 수정 optional 필드 전송, Record<string,unknown> 재조립 제거→JSON.stringify(data) 직접 전송
3. ui/src/entities/securities-account/api-client.test.ts — nullable/optional/사후입력/undefined 계좌번호 검증 테스트 (7건)
4. ui/src/entities/securities-account/index.ts — SecuritiesAccountUpdateRequest 내보내기 추가
5. ui/src/features/securities-manage/AccountForm.tsx — 생성 번호 optional, 수정 계좌명 항상 전송(빈값 포함), 번호만 조건부 전송, unreachable hasExistingNumber 분기 제거
6. ui/src/features/securities-manage/AccountRow.tsx — 번호 없으면 "미입력"+번호입력만(보기 버튼 미노출), 번호 있으면 보기/숨기기, 수정/번호입력 버튼
7. ui/src/features/securities-manage/SecuritiesManage.tsx — 체크박스 ON/OFF, 등록/해제, 행 잠금/에러(role=alert), 회사명 h3+체크박스 라벨 중복 제거(접근성 라벨 보존), 삭제 확인 문구 변경("청약 이력이 없는 계좌만 해제할 수 있습니다")
8. ui/src/features/securities-manage/api.ts — 수정 mutation에 accountNumber 지원
9. ui/src/features/securities-manage/AccountForm.test.tsx (신규, 5건)
10. ui/src/features/securities-manage/SecuritiesManage.test.tsx (체크박스 9건: 등록·실패·해제취소·409·투자자변경 등)
11. ui/src/features/investment-history/AccountSelector/AccountSelector.tsx — "미입력" 표시
12. ui/src/features/investment-history/AccountSelector/AccountSelector.test.tsx — null번호 테스트
13. ui/src/features/broker-connect/BrokerConnectPanel.tsx — accountNumber 필수 props, 번호 미입력 안내+마이페이지 링크
14. ui/src/features/broker-connect/BrokerConnectPanel.test.tsx — null 계좌번호 테스트 추가
15. ui/src/features/broker-sync/BrokerSyncPanel.tsx — accountNumber 필수 props, 번호 미입력 안내+마이페이지 링크(훅 규칙 준수)
16. ui/src/features/broker-sync/BrokerSyncPanel.test.tsx — null 계좌번호+마이페이지 링크 테스트
17. ui/src/pages/BrokerSyncPage/BrokerSyncPage.tsx — 계좌번호 전달
18. ui/src/pages/SecuritiesPage/SecuritiesPage.tsx — SecuritiesManage에 key={current.id} 추가(투자자 변경 시 remount로 stale 상태 방지)
19. docs/user-guides/manual/{pc.html,mobile.html} — 계좌등록 문구 갱신
20. docs/work-log/account-presence-frontend.md (이 파일)

## 검증 결과
- pnpm run typecheck: PASS
- pnpm run lint: PASS(0 errors, 4 warnings 모두 기존 파일)
- pnpm run format:check: 전체 기준선은 기존 실패 파일 포함해 FAIL 기록, 변경 파일 전부 Prettier 개별 검증 PASS
- pnpm run build: PASS
- pnpm run test: 영향 범위 69건 PASS. 전체 스위트의 InvestorGate 테스트 2건 실패는 원본 main에서도 동일하게 실패하는 선행 문제로, 본 변경과 무관함(원본 코드 stash 재현 확인)
- git diff --check: PASS

## 테스트 실제 건수 (영향 범위)
- entities/securities-account/api-client.test.ts: 7건
- features/securities-manage/AccountForm.test.tsx: 5건
- features/securities-manage/SecuritiesManage.test.tsx: 23건(기존 6 + 신규 9 + 체크박스 8)
- features/investment-history/AccountSelector/AccountSelector.test.tsx: 5건(기존 4 + 신규 1)
- features/broker-connect/BrokerConnectPanel.test.tsx: 8건(기존 7 + 신규 1)
- features/broker-sync/BrokerSyncPanel.test.tsx: 10건(기존 9 + 신규 1)
- pages/BrokerSyncPage: 기존 테스트 유지
- 영향 범위 합계: 70건 모두 PASS

## 2026-11-09 2차 보완 (controlled 경고 수정)
- ui/src/features/securities-manage/SecuritiesManage.tsx — renderFirm의 `checking = reg?.pending ?? false`로 boolean 확정(checked/disabled 일관 boolean, React controlled→controlled 경고 제거)
- ui/src/features/securities-manage/SecuritiesManage.test.tsx — 기존 체크박스 테스트(체크 시 등록 POST)에 console.error controlled/uncontrolled 경고 회귀 검증 1개 추가
- 검증: typecheck PASS, lint PASS(0 errors), build PASS, 변경 파일 Prettier 개별 PASS, securities-manage 테스트 21건 PASS, git diff --check PASS

## 2026-11-09 3차 보완 (동시 등록 결함 수정)
- ui/src/features/securities-manage/SecuritiesManage.tsx — handleFirmCheck를 async로 변경하고 create.mutate를 create.mutateAsync로 교체. TanStack MutationObserver가 최신 mutate 옵션만 보관해 앞선 firm의 onSuccess/onError가 누락되어 registering.pending이 영구히 남는 결함을 수정. 각 firm의 mutateAsync Promise를 try/catch/finally로 독립 정산.
- ui/src/features/securities-manage/SecuritiesManage.test.tsx — 연속 등록 테스트를 실제 동시성 테스트로 보강(POST 두 개를 서로 다른 deferred Promise로 대기, 둘 다 disabled 확인, 둘째 응답 먼저 완료 시 나증권만 enabled, 첫째 완료 후 둘 다 enabled). 변형 테스트 추가(첫째 reject/둘째 resolve → 첫째 alert+unchecked, 둘째 checked+enabled). getByRole checkbox name으로 직접 찾기.
- 검증: typecheck PASS, lint PASS(0 errors), build PASS, 변경 파일 Prettier 개별 PASS, securities-manage 테스트 23건 PASS, git diff --check PASS

## 보완 검증 범위
- 대상 테스트: src/features/securities-manage (23건 PASS)
- typecheck/build/prettier/기타: 전부 PASS
- Master 브라우저 검증은 모의API(vi.mock('@/shared/api/client.ts')) 기반으로 수행, 실제 백엔드 미연동

## 미완료/미실행
- 375/640/768/1024px 뷰포트 및 키보드 접근성 실측: NOT_RUN(브라우저 환경 미실행)
- docs/current-status.md, daily-log: Master 소유 문서로 본 워커가 수정하지 않음

## 미결정/비고
- "번호있는계좌만 보기/숨기기" 토글 UI는 spec에 명시된 토글이 없어 "미입력" 배지 표시로 구현함(별도의 보기/숨기기 토글 없음).
- InvestorGate는 accounts.length===0 기준(번호 유무 무관)을 그대로 유지, 계좌 존재 기반 이용 가능.
- "번호보기 API 호출 0회귀"는 AccountRow에서 번호 없는 계좌의 보기 버튼을 미노출해 API 호출 자체가 발생하지 않음.

## 2026-11-09 4차 보완 (동시 등록 테스트 deferred 라우팅 결함 수정 — 재개 작업)
- 작업자: frontend 워커 (OpenCode/OpenRouter 무료 모델, depth 1) — 이전 워커 일일 한도 중단 후 재개, 이전 진행분 보존
- 범위: ui/src/features/securities-manage/SecuritiesManage.test.tsx 체크박스 동시 등록 테스트 2건 수정, docs/work-log/account-presence-frontend.md append. 제품 코드·기타 파일 미변경(제품 구현은 Master 검증 통과 상태).
- 원인: deferred Promise resolver holder(`resolveFirst`/`resolveSecond`/`rejectFirst`)가 Promise 생성자에서 동기 할당되어 `if (!resolveFirst)` / `if (!rejectFirst)`가 항상 truthy로 평가. 결과: 두 증권사 POST가 같은 deferred Promise로 라우팅되어 첫 응답 resolve 시 두 mutation이 동시에 성공 처리(실패 검증), 첫 reject가 unmocked promise로 누수(실패/오류).
- 수정: POST 순서 추적 플래그(`firstIssued`) 추가 — 첫 POST→firstPromise, 둘째 POST→secondPromise. partial 실패 시나리오(첫째 실패/둘째 성공)에 둘째 성공 후 checked+enabled 검증 보강.
- 유지: deferred resolve 전 mock accounts[7]에 실제 account fixture(`account(31,7,1,{accountNumber:null})`, `account(32,7,2,{accountNumber:null})`) push 후 resolve → refetch 후 `screen.getByRole('checkbox', { name })`, checked+enabled 검증.
- 검증: pnpm exec vitest run src/features/securities-manage/SecuritiesManage.test.tsx → PASS(18/18), pnpm run typecheck → PASS, pnpm exec prettier --check src/features/securities-manage/SecuritiesManage.test.tsx → PASS, 루트 git diff --check → PASS.
- 미실행(지시된 검증 범위 외): pnpm run lint / pnpm run build. docs/current-status.md, daily-log 미수정(Master 소유 문서).

## 2026-11-10 5차 보완 (증권사 체크박스 label 최종 CSS 44x44 touch target — 재개 작업)
- 작업자: frontend 워커 (OpenCode Zen Flint 40B Free, OpenCode 경로 — OpenRouter 아님, depth 1) — 이번 재개
- 범위: ui/src/features/securities-manage/SecuritiesManage.styles.ts firmCheckbox label `min-width: 44px` 한 줄 추가, docs/work-log/account-presence-frontend.md append. 제품 로직·기존 UI·테스트 변경 없음.
- 결함: 새 checkbox label은 min-height:44px이나 폭이 input min-width:24px뿐이라 가이드 44x44 터치 대상에 미달. `min-width: 44px` 한 줄 추가로 label 44x44 확보.
- 검증: ui에서 pnpm run typecheck → PASS, pnpm run build → PASS, pnpm exec prettier --check src/features/securities-manage/SecuritiesManage.styles.ts → PASS, 루트 git diff --check → PASS.
- 로그 덧붙인 최종 Master 직접 검증: 대상7파일 68건 PASS, 전체 UI 874건 PASS / InvestorGate 기존 2 FAIL(선행 문제), typecheck/lint(기존 4 warning)/build/변경파일 Prettier PASS.
- 이번 재개모델: OpenCode Zen Flint 40B Free이고 OpenRouter가 아님.
- 제품 로직·다른 파일·commit/push·하위워커·current-status/daily-log 변경 없음. 이번 태스크 범위 외 검증(lint 등) 미실행.
