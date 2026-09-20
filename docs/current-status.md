# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-20

## 워크스페이스 구조 (2026-09-20부터 역할별 고정 3개, 절대 삭제하지 않음)

| 워크스페이스 | 경로 | 역할 | 현재 상태 |
|---|---|---|---|
| `backend` | `/Users/joy/orca/workspaces/vibe-ai-team/backend` | 백엔드 구현(B그룹) | 생성됨, 아직 미사용(Epic 1 이관 대기) |
| `frontend` | `/Users/joy/orca/workspaces/vibe-ai-team/frontend` | 프론트 구현(B그룹) | 생성됨, 아직 미사용 |
| `planning` | `/Users/joy/orca/workspaces/vibe-ai-team/planning` | 조사·기획·설계(A그룹) | 사용 중, main 기준 유지 |
| `account-authentication` (전환기) | `/Users/joy/orca/workspaces/vibe-ai-team/account-authentication` | Epic 1 구현 (정책 도입 전 시작) | Epic 1 완료, PR #41 사람 승인 대기 중. 승인·머지 후 `backend`로 이관 예정 |

## Epic 진행 현황 (로드맵 16개 Epic 기준)

| # | Epic | epic-slug | 상태 |
|---|---|---|---|
| 1 | 로그인 계정과 인증·인가 | account-authentication | **구현 완료**(SF-1~6, 34개 테스트 통과), PR #41 사람 승인 대기 |
| 2 | 투자자 관리 | investor-management | **Phase 0 전체 완료**(조사+spec+architecture, D-I-001~005 결정 포함), 머지됨. 다음: SF 분해 |
| 3 | 증권사 기준정보 관리 | securities-firm-catalog | 조사 완료, 머지됨 |
| 4 | 증권계좌 관리 | securities-account-management | 조사 완료, 머지됨 |
| 5 | 공모주 기준정보 관리 | ipo-catalog | 조사 완료, 머지됨 |
| 6 | 청약 기록 | subscription-management | 조사 완료, 머지됨 |
| 7 | 배정 결과 관리 | allocation-management | 조사 완료, 머지됨 |
| 8 | 분할 매도 기록 | sale-management | 조사 완료, 머지됨 |
| 9 | 비용 기록 | cost-recording | 조사 완료, 머지됨 |
| 10 | 수익·수익률 계산 | profit-calculation | 조사 완료, 머지됨 |
| 11 | 투자 내역 조회 API | investment-query-api | 조사 완료, 머지됨 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | 조사 완료, 머지됨 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | 조사 완료, 머지됨 |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | 조사 완료, 머지됨 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | 조사 완료, 머지됨 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | 조사 완료, 머지됨 |

**요약: 로드맵 16개 Epic 전체의 Phase 0(조사) 완료. Epic 1은 구현(기획→설계→구현→테스트)까지 완료, PR #41 사람 승인 대기 중. Epic 2는 Phase 0 전체(조사→기획→설계) 완료, 병합됨. Epic 3~16은 조사만 완료, 기획(spec)/설계(architecture)/구현은 아직 시작 전.**

## 다음 단계 후보
1. Epic 1 PR #41 사람 승인 및 머지 확인 → `account-authentication` 워크스페이스를 `backend`로 정식 이관
2. Epic 2(investor-management) 세부기능(SF) 분해(5.4) 및 구현 착수 (backend 워크스페이스, Epic 1 PR 머지 확인 후)
3. Epic 3(securities-firm-catalog) 기획(spec)·설계(architecture) 진행 (선행 조사 이미 완료됨, planning 워크스페이스에서 병렬 가능)

## 알려진 이슈/결정 사항
- Epic 1 구현 중 SessionRegistry 인스턴스 불일치, AuthService의 SessionAuthenticationStrategy 미호출, 만료 세션 기본 응답 코드 등 3건의 Spring Security 버그를 Master가 직접 진단하여 수정 지시함(자세한 내용은 `docs/work-log/account-authentication.md` 참고).
- `application-test.yml`에 평문 비밀번호(`vibe_local_password`) 하드코딩 — 추후 보안 검토 필요(미해결).
- 테스트가 Testcontainers 대신 실제 로컬 dev Postgres를 공유해서 사용 중 — 추후 격리 개선 검토 필요(미해결).
