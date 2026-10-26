# 애플리케이션 후속 리팩토링 계획 및 인수인계

> 기준 브랜치: `main`
> 기준 커밋: `5855945a` (2026-10-26 확인)
> 문서 상태: 사용자 승인으로 리팩토링 진행 중. 후속 단계별 변경은 해당 SF 범위와 수용 기준을 따른다.
> 목적: 세션이 끊겨도 다른 담당자가 사실관계와 다음 단계를 이어갈 수 있도록 현재 구조, 우선순위, 세부 작업, 검증 및 중단 지점을 한곳에 기록한다.

## 0. 재개 카드

다음 담당자는 먼저 저장소 `AGENTS.md`와 `docs/project-agent-guide.md`를 읽고, 이어서 `docs/roadmap.md`, `docs/current-status.md`, 이 문서, 관련 Epic의 research/spec/architecture/breakdown/work-log를 확인한다. backend 작업은 `docs/guide-backend.md`, frontend 작업은 `docs/guide-frontend.md`의 현재 규칙을 따른다.

이 문서는 계획 문서다. 실제 구현 전에는 현재 `main`과 열린 작업을 다시 확인하고, 아래의 Phase 0 계약·현황 조사를 갱신한다. 이어서 `docs/research/`, `docs/spec/`, `docs/architecture/`를 작성·갱신하고 Master 승인을 받은 뒤 `docs/breakdown/`에 3~8개 세부 기능(SF)을 정의한다. 이 순서를 건너뛰어 바로 코딩하지 않는다. 이미 완료된 Epic 20이나 과거 backend 리팩토링을 다시 열지 말고, 완료 기록과 현재 가이드를 기준으로 남은 항목만 선별한다.

개발 DB를 사용하는 검증은 로컬 `.envrc`의 `DB_PASSWORD`를 사용한다. Docker 컨테이너 설정이나 배포 DB 자격 증명을 개발 DB 비밀번호의 근거로 사용하지 않는다. 비밀값을 문서·명령 출력·로그·커밋에 남기지 않는다. DB 작업은 프로젝트의 DB 슬롯 절차를 따르고, 운영 DB에는 연결하거나 마이그레이션하지 않는다.

## 1. 범위와 목표

목표는 대규모 재작성 없이 현재 기능의 API·화면 동작과 데이터 불변식을 고정한 뒤, 변경 비용과 회귀 위험이 높은 경계를 작게 정리하는 것이다. 사용자는 청약 일정·상태, 대시보드, 투자 내역, 계좌·청약·배정·매도 기능을 현재와 같은 계약으로 사용한다. 우선순위는 **계약 보호 → 프론트 데이터 경계 → 복잡한 조회·동기화 서비스 → 도메인 경계 → 반복 검증 자동화**다.

포함 후보: React Query 캐시와 취소 규칙 점검, API 응답 런타임 검증 점검, URL 필터 상태의 재사용성, 페이지 조합 책임, 투자내역 배치 조회/조립 경계, IPO 동기화 오케스트레이션, 계좌·기록 도메인 규칙의 응집도, 회귀·성능 검증.

기본 제외: 사용자 기능 추가, API 경로·필드·오류 의미 변경, DB 스키마/마이그레이션, CQRS 프레임워크 도입, 대규모 FSD 재배치, 라이브 DART·증권사 계정 호출, 광범위한 테스트 파일 분할, 의존성 추가. 조사에서 필요성이 입증되면 별도 결정과 계획 변경으로 다룬다.

각 변경은 독립적으로 검토·병합·되돌릴 수 있어야 한다. 동작을 바꾸는 요구가 발견되면 리팩토링에 섞지 말고 별도 제품 변경으로 분리한다.

## 2. 현재 기준과 이미 완료된 작업

- 기준 커밋은 위에 적은 `5855945a`; 구현 착수 시 `git status`, HEAD, 원격 상태와 진행 중인 PR을 다시 확인한다.
- 프론트엔드 아키텍처 Epic 20은 `#259`로 2026-10-01 병합 완료다. FSD 유사 레이어 경계, 슬라이스 공개 API, `fsd-boundaries.test.ts`, React Query 키 factory, GET 요청 `AbortSignal`, mutation invalidation, Zod 응답 검증, React Compiler 및 접근성 규칙이 `docs/guide-frontend.md`에 정리돼 있다. 이 규칙의 재구현이나 구조 전체 재작성은 범위가 아니다.
- Epic 20의 기존 증거는 `docs/research/frontend-architecture-refactor.md`, `docs/spec/frontend-architecture-refactor.md`, `docs/architecture/frontend-architecture-refactor.md`, `docs/breakdown/frontend-architecture-refactor.md`, `docs/work-log/frontend-architecture-refactor.md`다. 이들은 당시 스냅샷이므로 현재 코드·가이드와 다른 부분은 현재 main을 따른다.
- 과거 backend 리팩토링 산출물도 완료 기록으로 취급한다. 현재의 측정 가능한 복잡도와 테스트 근거 없이 과거 작업을 다시 열지 않는다.
- Postman/OpenAPI 계약 파일은 기준 시점 저장소에서 확인되지 않았다. 현재 계약의 근거는 API/spec/DTO, MockMvc 테스트, UI Zod 스키마와 fixture다. 이번 계획만을 이유로 Postman 초기화나 외부 workspace 변경을 하지 않는다. 공유 계약이 필요한 소비자·팀이 확인되면 로컬 OpenAPI/Postman 계약 도입을 별도 검토한다.
- 현재 프론트엔드 가이드는 query key, 취소 신호, 응답 Zod 파싱, 401 공통 처리, mutation 재시도 금지 등 이미 합의된 정책을 포함한다. 옛 연구 문서의 발견 사항을 현재 결함으로 간주하지 말고 실제 사용처를 재검사한다.

### 2026-10-26 현재 진행 상태

기준 커밋 `5855945a`에서 아래 변경을 한 작업 묶음으로 진행했다. 변경은 현재 `main` 작업 트리에 미커밋 상태이며, 사용자 지시에 따라 모든 구현을 모은 뒤 검증했다.

| 단계 | 진행 내용 | 상태 |
|---|---|---|
| 6.1 프론트 데이터 경계 | Allocation/Sale/Subscription 상세 GET의 Zod schema, `z.infer`, `AbortSignal` 적용. 편집 dialog queryFn 연결 및 응답 계약 테스트 보강 | 완료 |
| 6.2 화면 URL 상태 | 청약 일정 기간 URL updater를 순수 함수로 이동. 나머지 query 보존, 범위 순서 보정, 원본 params 불변성 테스트 | 완료 |
| 6.2 화면 결과 조합 | DashboardPage의 탭별 결과 변환·렌더링을 page slice sibling으로 분리. query·탭·기간 상태는 페이지에 유지 | 완료 |
| 6.3 투자내역 조회 | `InvestmentHistoryReadModelLoader`로 참조 데이터 배치/상세 조회 분리. Query service의 미사용 의존성 제거 및 빈 페이지 조회 생략 검증 | 완료 |
| 6.4 IPO 동기화 | 최근일/월 범위 경로에서 회사별 DS006 검증·upsert 처리를 공통화하고 fetch 방식과 기존 실패 정책 유지 | 완료 |
| 6.5 증권계좌 경계 | 투자자 및 계좌 소유권 lookup/검증을 `SecuritiesAccountOwnershipVerifier`로 추출 | 완료 |
| 6.5 도메인 입력 검증 | SaleService 중복 하한 검사 통합, Allocation update 중복 음수 검사 제거 | 구현·검증 완료 |
| 6.1 GET 응답 경계 후속 | 공지 팝업의 204 → 빈 목록 동작을 유지하며 `apiGetParsed`로 통일, API client 테스트 추가 | 구현·검증 완료 |
| 6.1 응답 parse 경계 통합 | GET·본문 응답 요청에서 반복되던 fetch+Zod parse를 `apiFetchParsed`로 통합하고 GET helper가 이를 재사용 | 구현·검증 완료 |
| 6.1 feature query hook 중복 | 증권사 계좌 등록 feature의 중복 증권사 조회 hook 제거, entity hook 재사용 | 구현·검증 완료 |
| 6.1 공모주 등록 응답 검증 | 기존 IPO 응답 schema로 POST /ipos 응답도 검증 | 구현·검증 완료 |
| 6.1 투자 기록 mutation 응답 검증 | 청약·배정·매도 POST/PATCH가 기존 entity response schema를 사용 | 구현·검증 완료 |
| 6.1 추가 mutation 응답 검증 | 브로커 credential POST/PUT 및 관리자 공지 POST/PATCH의 기존 schema 적용 | 구현 완료·종합 검증 대기 |
| 6.1 계좌·투자자·가입 응답 경계 | 투자자/계좌 등록·계좌 수정/기본 지정·원본번호 공개·회원가입 응답 검증 | 구현 완료·종합 검증 대기 |
| 6.1 남은 응답 DTO 검증 | 알림 subscribe/unsubscribe SuccessResponse 및 broker sync SaleResponse 목록 검증 | 구현 완료·종합 검증 대기 |
| 6.5 청약 수정 계좌 조회 중복 | 검증된 계좌 객체 재사용, 기존 소유권/투자자 관계 유지 | 구현 완료·종합 검증 대기 |
| 6.4 동기화 결과 초기화 중복 | 최근일·월 범위 실행이 `emptyResult()` factory를 공유 | 구현 완료·종합 검증 대기 |
| 6.4 IPO 영속화 경계 | 기존 조회/수동 보호/생성·갱신을 `IpoSyncPersistence`로 이동, transaction은 coordinator 소유 | 구현 완료·종합 검증 대기 |
| 6.1 mutation 캐시 범위 특성화 | 투자 기록 수정/삭제와 브로커 확정의 정확한 무효화 prefix 테스트 추가 | 구현 완료·종합 검증 대기 |
| 6.1 query key factory 누락 | 공지사항, IPO 동기화 상태 및 전체 투자내역 key 조립을 entity factory로 이전하고 key/prefix 테스트 추가 | 구현·검증 완료 |
| 6.2 청약 URL 날짜 검증 | 형식뿐 아니라 실제 날짜와 윤년을 검사 | 구현·검증 완료 |
| 6.2 청약 일정 집계 | IPO별 투자내역 그룹을 단일 순회로 구성하고 복수 기록 순서 테스트 추가 | 구현·검증 완료 |
| 6.2 매도 등록 폼 캡처 적용 | 캡처 대기·필드 적용·표시 상태를 `useCaptureFill`로 분리, 기존 `SaleForm` shape 및 통합 테스트 유지 | 구현·검증 완료 |
| 6.6 검증 자동화 | 기존 workflow 부재를 확인하고 UI/backend 병렬 GitHub Actions 품질 job 추가 | 구현 완료·hosted runner 검증 대기 |
| 공지 서비스 중복 조회 정리 | 관리자 상세·수정·삭제의 활성 공지 조회와 동일한 404 처리를 private helper로 통합 | 구현·검증 완료 |
| API mutation 응답 검증 | IPO 등록 및 청약·배정·매도 등록/수정 응답을 기존 Zod schema로 검증 | 구현·검증 완료 |
| 증권사 조회 hook 중복 제거 | 등록 feature의 중복 hook 제거 및 entity hook 재사용 | 구현·검증 완료 |
| 계좌별 투자내역 DTO 확장 | 이번 리팩토링 묶음에서 API/DTO 변경은 하지 않음 | 미착수 |

### 최종 검증 현황

- Backend: `test`, `checkstyleMain`, `checkstyleTest`, `spotlessCheck`, `build` 모두 PASS. 첫 종합 실행에서 `SecuritiesAccountService.java` 공백 한 건이 Spotless에 걸려 수동 수정했고, 재실행에서 전체 task 성공(테스트 task는 변경 없는 결과를 재사용).
- UI: 90 test files / 709 tests PASS, lint PASS, typecheck PASS, format:check PASS, production build PASS. 첫 실행에서 편집 테스트 mock이 GET 옵션을 쓰기 요청으로 오인하는 4건 실패가 있어 mock을 수정한 뒤 전체 테스트가 통과했다.
- `git diff --check`: PASS.
- 백엔드는 로컬 `.envrc`에서 환경 변수를 읽고 격리 DB 슬롯 4를 사용했다. 시크릿은 기록하지 않았다.
- 브라우저 실사용 검증은 실행하지 않음(NOT_RUN). Postman/OpenAPI 산출물 또는 외부 workspace 변경은 이번 작업에 포함하지 않았다.
- 각 트랙의 상세 작업과 검증 이력은 `docs/work-log/{investment-history-read-model-refactor,ipo-sync-company-processing-refactor,securities-account-ownership-boundary,allocation-sale-read-boundary,subscription-list-url-state-refactor,dashboard-results-composition-refactor}.md`에 append-only로 기록한다.

### 2026-10-26 누적 리팩토링 최종 검증

- UI: 96 test files / 730 tests PASS, lint PASS, typecheck PASS, format:check PASS, production build PASS.
- Backend: 124 test suites / 1,099 tests PASS (failures/errors/skipped 0), `checkstyleMain` PASS, `checkstyleTest` PASS, `spotlessCheck` PASS, `build` PASS. 개발용 `.envrc` 환경과 격리 DB 슬롯 4를 사용했다.
- `git diff --check`: PASS.
- 첫 검증에서 새 UI 테스트 fixture의 응답 본문 재사용, IPO key mock 누락, Allocation DTO fixture의 `deletedAt` 누락, 기존 Sale form 분리의 unused import, Sale test 포맷 위반 및 Announcement helper 포맷 위반을 확인해 수정했다. 수정 후 최종 전체 UI 검증과 backend 전체 build까지 통과했다.
- GitHub hosted workflow, 브라우저 수동/E2E, 투자내역 성능 기준 측정은 실행하지 않았다: `NOT_RUN`.

### 이어서 진행할 작업

1. Dashboard 결과 조합 분리는 완료했고 UI 종합 검증도 PASS했다. 상세 결과는 아래 2026-10-26 후속 단계 현황과 work-log를 참고한다.
2. 매도 등록·수정의 금액 하한 중복을 helper로 통합했다. 전체 backend 테스트·Checkstyle·Spotless·build에서 검증 완료(PASS).
3. Allocation update의 중복 음수 검사를 제거했다. 기존 음수 PATCH 400 계약을 포함해 backend 전체 검증 완료(PASS).
4. 공지 팝업 GET의 204 → 빈 목록 동작을 유지하며 `apiGetParsed`로 통일하고 테스트했다. UI 전체 검증 완료(PASS).
5. 공지사항 및 IPO sync status query key, 전체 투자내역 키를 entity factory로 통합하고 테스트했다. UI 전체 검증 완료(PASS).
6. 청약 일정 URL에서 실제로 존재하지 않는 날짜를 거부하고 윤년을 허용하도록 보완했다. UI 전체 검증 완료(PASS).
7. 청약 일정 집계에서 IPO별 배열을 매 입력마다 재복사하던 그룹핑을 한 번의 순회 append로 변경했다. 복수 기록 순서/건수 테스트를 추가했으며, 이후 투자내역 전체를 모을 때의 불필요한 복사를 줄인다.
8. `useSaleForm`에서 캡처 적용 대기/effect/표시 상태를 `useCaptureFill`로 분리했다. 기존 `SaleForm` 반환 구조를 유지하고 캡처 적용·손익 비용 역산·안내 통합 테스트를 추가했다.
9. `MainLayout`은 이미 `NavGroup`·`InvestorSwitcher`로 분리되어 있고 로그인/게스트 흐름 테스트가 있으므로 추가 분리는 보류한다. Subscription/Allocation은 조사 결과 명확한 공통 경계가 아직 없다. 손익 수학은 이미 `entities/sale/saleMath.ts`에 있어 재추상화하지 않는다.
10. 투자내역 계좌 식별 DTO 확장은 기능 요구와 직접 연결될 때만 별도 범위로 재검토한다.

### 2026-10-26 후속 단계 현황

### 종합 검증 이후 후속 변경 (2026-10-26)

- 위 최종 검증 PASS는 당시 작업 트리 기준이다. 이후 리팩토링이 추가됐으므로 현재 전체 변경 묶음의 검증 상태는 `NOT_RUN`이다.
- broker credential POST/PUT 응답에 `brokerCredentialSchema`, announcement admin POST/PATCH 응답에 `announcementSchema`를 적용했다. backend 응답 DTO와 기존 schema를 대조하고 정상/잘못된 응답 및 요청 옵션 테스트를 추가했다.
- 이 변경 뒤 전체 UI/backend 테스트·정적 검사·빌드·diff 검증은 실행하지 않았다. 남은 리팩토링을 마친 다음 한 번에 실행한다.
- 후속 도메인 점검에서 `SubscriptionService.update`가 새 계좌 ID를 검증한 뒤 같은 계좌를 다시 조회하는 중복을 확인해 첫 조회 결과를 재사용하도록 했다. same-investor 계좌 변경 MockMvc 테스트를 추가했다. backend 검증은 `NOT_RUN`.
- 추가 후보를 재검토했다. `MainLayout`은 이미 `NavGroup`/`InvestorSwitcher`로 분리되어 더 쪼개면 작은 상태 로직만 추상화하므로 추가 변경하지 않는다. `AnnouncementService`의 `ResponseStatusException`을 공통 `ApiException`으로 바꾸면 오류 JSON 계약이 달라질 가능성이 있어, 계약 변경 없이 진행하는 이 리팩토링 범위에서는 보류한다. 관련 API 계약을 별도 변경할 때 응답 형식 특성화 테스트부터 추가한다.
- 이 오류 후보의 API 회귀 기준으로 Announcement controller test에 실제 MockMvc 계약(400 validation 및 404 not-found, 두 응답 모두 빈 body)을 고정했다. 첫 종합 실행에서 detail body가 비어 있음을 확인해 잘못된 기대를 수정했으며 예외 구현은 그대로다.
- UI API 응답 parse 감사에서 응답 본문을 반환하는 POST/PUT/PATCH는 기존 schema를 적용했다. 남은 mutation은 void/본문 없는 API 계약으로 확인했다. production query key는 factory를 사용하며 남은 inline 배열은 테스트 fixture였다. 전체 query invalidation gap matrix와 성능 측정은 마지막 검증/측정 단계에서 완료한다.
- 추가 감사에서 투자자·증권계좌 등록과 계좌명 수정/기본 지정 응답이 feature 내부의 타입 단언으로 남아 있어 entity schema 경계로 이동했다. 계좌번호 전체 공개 응답과 회원가입 응답도 DTO 기반 schema를 적용했다. 민감 문자열은 계속 mutation 반환 문자열에만 존재하고 `gcTime: 0`을 유지한다.
- entity/feature 전체 `apiFetch` 사용처를 다시 대조해 notification subscribe/unsubscribe의 message 응답과 broker confirm의 SaleResponse 목록을 추가 보완했다. broker confirm은 Sale entity schema를 써야 하므로 broker entity transport에서 `features/broker-sync`로 옮겼다. 호출부는 이전처럼 `Promise<void>`로 유지한다.
- 후속 query key 감사에서 `useIpo(undefined)`의 inline invalid-detail key 하나를 발견해 `ipoKeys.invalidDetail()`로 이전했다. 기존 key 값은 동일하며 테스트를 추가했다. production GET api-client 전체가 `apiGetParsed`와 선택 `AbortSignal`을 사용하고, 나머지 inline key 검색 결과는 test fixture였다.
- IPO sync 후속 SF-2에서 최근일/월 범위 동기화 결과의 동일 초기화 builder를 `emptyResult()` 단일 factory로 통합했다. 매 호출마다 새 failure list를 만들고 빈 결과의 기존 기본값을 유지한다. 관련 기존 테스트와 backend 전체 검증은 마지막 묶음에서 실행한다: `NOT_RUN`.
- mutation 무효화 감사를 마무리하며 투자 기록 수정/삭제가 투자내역·수익·대시보드, broker 확정이 해당 계좌 미리보기와 세 투자 view를 각각 무효화하는지 회귀 테스트를 추가했다. 남은 직접 `apiFetch`는 DELETE/204 및 로그인·로그아웃의 본문 없는 응답이다. 추가 테스트는 실행하지 않았다: `NOT_RUN`.
- `IpoSyncService`의 persistence 책임을 `IpoSyncPersistence`로 이동했다. DART fetch/분류와 건별 transaction은 coordinator에 남기고, 저장 결과 enum으로 기존 결과 counter만 반영한다. 일반/월 범위 기존 테스트가 새 collaborator를 구성하도록 바꿨으며 검증은 `NOT_RUN`이다.

> 아래 구현 메모의 당시 `NOT_RUN` 표기는 작업 시점 기록이다. 최종 상태는 바로 위의 **누적 리팩토링 최종 검증** 절을 기준으로 한다.

- 대시보드 탭 결과 변환·렌더링을 `ui/src/pages/DashboardPage/DashboardTabContent.tsx`와 전용 co-located styles로 이동했다. `DashboardPage`는 query, loading/error, tab/period state 및 reset/invalidation을 유지한다.
- 종합 요약/투자자 fallback, 투자자 수에 따른 접힘, 빈 결과 reset 동작의 컴포넌트 테스트를 추가했다.
- 검증: UI 91 test files / 712 tests, lint, typecheck, format:check, build, `git diff --check` 모두 PASS. 브라우저 수동 확인은 NOT_RUN.
- 이전 리팩토링 묶음은 상기 표의 PASS 기록대로 검증 완료다. 현재 미커밋 변경 전체를 한 브랜치에서 이어 진행 중이다.
- `SaleService`의 등록·수정 금액 하한 중복은 helper로 통합했다. 이 변경은 아직 검증하지 않았다(NOT_RUN).
- `AllocationService.update`의 중복 음수 사전 검사 제거와 공지 팝업 GET의 표준 parse 경계 적용·테스트 추가가 뒤따랐다. 관련 UI/backend 검증은 모두 NOT_RUN이며, 각 누적 묶음이 끝날 때 실행한다.
- 공지사항 및 IPO sync status의 inline query key를 entity factory로 이전하고 기존 key/prefix를 확인하는 테스트를 추가했다. UI 검증은 계속 NOT_RUN이다.
- `useAllInvestmentHistory`의 key suffix도 `investmentHistoryKeys` factory로 이동했다. 기존 list prefix와 전체 목록 key 관계를 테스트로 고정했다.
- 청약 일정 URL parser의 실제 날짜 검증도 보완했다. UI 테스트는 누적 frontend 리팩토링 묶음 종료 전까지 NOT_RUN이다.
- IPO별 투자내역 그룹핑의 반복 배열 복사도 제거하고 복수 청약 유지 테스트를 추가했다. UI 검증은 계속 NOT_RUN이다.
- `useSaleForm` 캡처 적용 책임을 hook으로 분리하고 기존 폼 API를 통한 회귀 테스트를 추가했다. UI 검증은 누적 작업 종료 전까지 NOT_RUN이다.
- 변경된 UI 소스는 Prettier로 포맷했다. 테스트·lint·typecheck·format:check·build는 아직 실행하지 않았다(NOT_RUN).
- 기존 `.github/workflows`가 없어 `.github/workflows/quality.yml`을 추가했다. main push/PR에서 Node 22+pnpm 10 UI 검사, Java 21+일회성 PostgreSQL 17 backend 검사를 수행한다. workflow hosted runner 결과는 `NOT_RUN`; branch protection은 변경하지 않았다.
- 공지 서비스의 관리자 상세·수정·삭제에서 반복되던 ID 조회와 동일한 404 예외 생성을 `findExistingAnnouncement`으로 통합했다. 활성 공지 조건과 오류 응답은 유지했다. 사용자 지시에 따라 종합 검증 전까지 검사는 실행하지 않는다: `NOT_RUN`.
- `apiFetchParsed`를 추가해 GET과 본문 응답을 반환하는 요청이 공통 fetch+Zod parse를 사용하게 했다. IPO 동기화 POST 및 알림 설정 PUT/테스트 POST의 응답 파싱을 이동하고 기존 GET helper도 공통 구현을 재사용한다. 요청 method/body와 응답 계약은 유지하며 공통 helper 회귀 테스트를 추가했다. UI 검증은 누적 frontend 리팩토링 종료 때 수행한다: `NOT_RUN`.
- `securities-account-register` feature 안의 `useSecuritiesFirms`가 entity의 동명 hook과 query key, fetch 및 AbortSignal 전달이 완전히 같음을 확인했다. feature wrapper를 제거하고 `@/entities/securities-firm` 공개 API를 직접 사용하도록 변경했다. 동작과 캐시 계약은 유지하며 UI 종합 검증은 누적 묶음 종료 시 수행한다: `NOT_RUN`.
- `POST /ipos` 응답이 backend `IpoResponse`를 반환하는 것을 확인하고, 이미 `GET /ipos`에서 사용하는 `ipoSchema`를 `registerIpo`에도 적용했다. 요청/응답 shape는 바꾸지 않으며 불완전한 응답을 거부하는 회귀 테스트를 추가했다. UI 종합 검증은 누적 묶음 종료 때 진행한다: `NOT_RUN`.
- 청약·배정·매도 POST/PATCH는 이미 해당 entity의 GET response schema와 타입이 정의되어 있어 `apiFetchParsed`로 연결했다. 요청 method/body는 유지하고 mutation 성공 응답에도 GET과 같은 런타임 검증을 적용한다. 각 client에 정상/잘못된 응답 회귀 테스트를 보강했으며 UI 종합 검증은 누적 변경 종료 때 수행한다: `NOT_RUN`.
- 누적 구현 묶음의 backend/UI 전체 품질 검증은 완료했다. 남은 검증 후보는 GitHub hosted workflow 실행, 브라우저 수동/E2E, 투자내역 성능 기준 측정이며 위 최종 검증 절에 `NOT_RUN`으로 기록했다.

### 코드에서 확인된 조사 출발점

아래는 리팩토링 후보를 찾기 위한 출발점이지, 모두 결함이라는 뜻은 아니다. 각 SF 착수 전 사용처와 테스트를 재확인한다.

| 후보 | 현재 관찰 | 조사 질문 |
|---|---|---|
| `backend/.../IpoSyncService.java` | 약 647 LOC. 외부 일정 탐색·공시 수집·동기화 흐름·저장·일정 병합·결과 기록이 한 서비스에 모여 있다. | 실제 동기화 분기와 실패 정책을 테스트로 고정할 수 있는가? 외부 I/O와 트랜잭션의 경계는 명확한가? |
| `backend/.../InvestmentHistoryQueryService.java` | 약 267 LOC, 여러 조회 의존성이 주입되며 구독·배정·매도와 IPO/투자자/계좌/증권사 데이터를 조합한다. 배치 조회 구조가 있다. | 기존 배치 로딩을 유지하면서 조회 모델 로딩과 DTO 조립을 분리할 수 있는가? 쿼리 수와 응답 시간을 보존하는가? |
| `backend/.../SecuritiesAccountService.java` | 약 330 LOC, 계좌 등록·수정·기본 계좌·소유권·민감값 마스킹 책임이 함께 있다. | 정책/명령 경계를 나눌 실익이 있는가? 소유권, 기본 계좌, 마스킹이 한곳에서 계속 강제되는가? |
| `ui/src/pages/SubscriptionListPage/SubscriptionListPage.tsx` | URL 기간 읽기/정규화, 투자자·IPO·전체 투자내역 조회, 일정 집계, 로딩/오류/빈 상태 조합을 한다. 순수 집계는 `features/subscription-list/subscriptionSchedule.ts`에 있다. | 현재 116 LOC의 조합이 실제로 중복되는가? URL 규칙을 공용화할 명확한 두 번째 사용처가 있는가? |
| Dashboard / sale form / layout | 페이지 조합과 탭, OCR·선택·수익 계산 등 여러 UI 책임의 후보가 있다. | 실제 변경 빈도·테스트 어려움·복잡도가 분리를 정당화하는가? 파일 길이만으로 쪼개지 않는다. |
| API 응답 경계 | `shared/api/client.ts`의 일반 `apiFetch<T>`는 JSON을 타입 단언한다. 현재 가이드의 `apiGetParsed`와 엔티티별 Zod 파싱이 적용된 경로도 있다. | 호출 경로별로 런타임 검증 적용 여부를 목록화하고, 가이드가 요구하는 미적용 경로만 좁혀 보완한다. |

`InvestmentHistoryQueryService`의 사용되지 않는 `EntityManager` 주입 여부, 서비스의 실제 의존성 개수, UI 각 후보의 현재 LOC는 착수 시 다시 확인한다. 수치는 분할 자체의 근거가 아니라 조사 단서다.

## 3. 설계 원칙

1. **동작 우선 보존**: 응답 JSON(필드·nullable·정렬·페이지네이션), HTTP 상태/오류, URL, 권한, 화면 상태를 characterization test로 고정한다.
2. **데이터 경계에서 검증**: UI는 기존 Zod/public API 패턴을 따르고, backend는 DTO·MockMvc 계약으로 API를 보호한다. 내부 타입만 바꿔 외부 계약을 흔들지 않는다.
3. **외부 의존성은 격리**: DART·비공식 일정 원천·증권사 호출은 mock/fake로 시험한다. 실제 고객/증권 계정이나 운영 외부 API를 테스트에 사용하지 않는다.
4. **배치 접근 유지**: 투자내역의 Allocation/Sale/기준정보 조회를 레코드별 조회로 바꾸지 않는다. N+1 회귀와 응답 지연을 차단한다.
5. **작은 수직 변경**: SF별 테스트·구현·문서를 함께 끝내고, 완료 증거를 남긴다. 측정 없이 추상화 계층이나 범용 프레임워크를 추가하지 않는다.
6. **금융 기록 불변식 보존**: 계정 소유권, 계좌·투자자 관계, 중복 청약, 기본 계좌, 배정·매도 수량, 삭제 행 제외 규칙을 변경하지 않는다. 배정 0주가 유효하다는 기존 규칙도 회귀 기준이다.
7. **기존 프론트 규약 준수**: React Query 키 factory, public slice API, URL query 단일 출처, `AbortSignal`, Zod schema, mutation invalidation/재시도 정책 및 접근성 규칙을 따른다.

## 4. 우선순위와 의존성

| 우선순위 | 트랙 | 이유 | 착수 전 조건 |
|---|---|---|---|
| P0 | API/화면/권한 기준선과 복잡도 현황표 | 나머지 변경의 안전망. 현재 규칙과 완료 작업을 중복 구현하는 위험을 줄임 | 최신 main, 테스트 설정, 현재 이슈/PR 확인 |
| P1 | 프론트 데이터 경계 감사 및 필요한 누락 보완 | API 타입 단언, 캐시/취소/무효화는 런타임 오류와 오래된 화면의 주요 경계 | `guide-frontend.md` 대조 후 실제 누락만 선택 |
| P1 | 투자내역 조회 로더/조립 경계 | 여러 데이터 원천과 배치 규칙이 모인 읽기 경로 | 응답/권한/쿼리 수 기준선 |
| P1 | IPO 동기화 흐름 분리 | 외부 데이터 실패·수동 일정 보존·동시 실행 제한이 얽힌 큰 서비스 | 기존 동기화 동작과 장애 정책 characterization |
| P2 | URL 상태와 페이지 조합 정리 | 화면 책임·중복이 실제로 확인된 범위만 개선 | 중복 사용처나 테스트성 등 구체 근거 |
| P2 | 계좌/청약/배정/매도 정책 응집도 | 비즈니스 규칙의 추적성과 테스트성을 높임 | 각 규칙의 현재 소유 위치와 회귀 테스트 |
| P3 | 지속 검증 자동화/경계 테스트 | 위 변경의 회귀를 CI/로컬에서 반복 차단 | 저장소 기존 파이프라인 중복 여부 확인 |

독립 트랙은 역할별 준비된 작업공간이 실제로 있을 때만 병렬화한다. 같은 파일·계약·서비스를 다루거나 선행 결정이 필요한 작업은 순차 처리한다. 세부 배정 시 root `AGENTS.md`의 작업공간, 무료 모델, task dependency 및 검증 규칙을 따른다.

## 5. Phase 0 — 기준선과 계약 조사

### 범위

현재 코드/문서/테스트를 기준으로 다음 계약표를 만들고, 각 항목의 기존 테스트와 공백을 기록한다.

| 흐름 | 고정할 계약 |
|---|---|
| `GET /investment-history` | 인증 계정/투자자 범위, 필터·페이지 경계, 응답 nullable 필드·정렬, 배정/매도 집계, 오류 상태 |
| `GET /ipos` | 인증/게스트 접근 정책, 응답 nullable 값, 일정 필드와 정렬, 빈 데이터/실패 UI |
| 청약·배정 등록 | 계정/투자자/계좌/IPO 소유권, 중복 조건, 수량 범위(배정 0 포함), API 오류 의미 |
| `/subscriptions` | 날짜 쿼리 기본값·유효성·replace 동작, 투자자 전환, 일정 기준 상태, 로딩/오류/빈 상태 |
| IPO 자동 동기화 | 외부 원천 실패/부분 성공/재시도, 중복 실행 제한, 수동값 보존, 한 회사 실패의 영향 |
| 계좌 관리 | 소유권 검증 순서, 기본 계좌 전환, 마스킹/민감정보 로그 금지, 비활성화 동작 |

Backend는 MockMvc/서비스 테스트에서 상태 코드, JSON shape, nullability, 정렬, 권한 및 예외를 고정한다. UI는 기존 fixture를 실제 DTO 모양과 일치시키고 페이지의 주요 사용자 상태를 테스트한다. 가능하면 end-to-end 시나리오를 `미청약 → 복수 계좌 청약 → 일부 결과 대기 → 전부 배정 결과(0주 포함)`까지 연결한다. 외부 통신은 fake/mock으로 대체한다.

투자내역 조회는 100/500/1,000건 규모 fixture 또는 재현 가능한 로컬 데이터로 쿼리 수와 응답 시간을 기록한다. 해당 측정이 현재 테스트 환경에서 재현 불가능하면 지표를 임의로 만들지 않고 도구/데이터 공백과 대안을 기록한다. 별도 IPO 일정 상태 API는 데이터량·성능 측정에서 필요가 입증될 때에만 별도 설계한다.

### 완료 기준

- 계약별 근거 테스트/문서 위치, 공백, 위험, 책임 모듈을 표로 남긴다.
- 현재 코드와 `guide-frontend.md`/`guide-backend.md`의 차이를 확인한다.
- 구현 대상이 아닌 이미 충족된 규칙은 완료/유지로 표시한다.
- 이어지는 research/spec/architecture/breakdown에 사용할 수 있는 우선순위와 독립성 판단이 있다.

## 6. 실행 트랙과 단계별 계획

### 6.1 프론트엔드 데이터 경계

**조사 대상**: `ui/src/app/queryClient.ts`, 엔티티별 `queryKeys.ts`, GET `api-client.ts`, `shared/api/parsed.ts`, Zod `types.ts`, `app/mutation-invalidation.test.tsx`, 관련 화면·훅.

1. 모든 query key 생성이 entity factory를 쓰는지, prefix 관계와 사용자/필터 범위가 맞는지 확인한다. 누락·혼합 모양이 있는 키만 factory와 테스트를 정리한다.
2. 모든 GET 클라이언트가 선택 `signal?: AbortSignal`을 받아 queryFn의 `signal`을 전달하는지 확인한다. 가이드와 다른 경로만 수정하고 취소 동작을 테스트한다.
3. 성공 mutation의 정확한 prefix invalidation을 확인한다. 투자내역·수익·대시보드 등 실제 영향 데이터만 invalidate하고 광범위한 전체 캐시 삭제는 피한다.
4. API 응답 Zod 검증 현황을 도메인별로 조사한다. 가이드가 이미 지정한 투자내역/청약, IPO, 대시보드/수익, 계좌/투자자 등의 우선순위를 따른다. 일반 `apiFetch<T>` 전체를 무차별 변경하지 말고, 각 api-client의 parse 경계와 `z.infer` 타입을 일치시킨다.
5. 스키마 mismatch(`ZodError`)와 네트워크/API 오류의 표시·retry를 구분한다. 4xx와 Zod mismatch 즉시 실패, 네트워크/5xx 제한 재시도, mutation retry 0 규칙을 보존한다.
6. URL 날짜 유틸이 쓰이는 경우 `YYYY-MM-DD` 실제 날짜 검증을 확인한다. `2026-02-30`처럼 문법은 맞지만 존재하지 않는 날짜를 거부하고, 한국 로컬 날짜 및 query replace 원칙을 지킨다.

**완료 기준**: query key/취소/invalidation/schema 각 변경에 회귀 테스트가 있고, 기존 loading/error/401 동작과 URL이 동일하다. 누락이 없으면 코드를 바꾸지 않고 Phase 0에 `이미 충족` 근거만 기록한다.

### 6.2 화면 조합과 URL 상태

대상 후보는 `SubscriptionListPage`, Dashboard 페이지, 매도 등록의 `useSaleForm`, `MainLayout`이다. 전부 분리하지 않는다. 중복 로직, 복잡한 테스트 경계, 변경 빈도 중 하나 이상을 증거로 제시한 후보만 대상으로 한다.

- `SubscriptionListPage`: 순수 IPO 일정 집계는 이미 `features/subscription-list/subscriptionSchedule.ts`에 있다. 페이지는 현재 URL 기간/투자자, IPO 및 전체 투자내역 query와 결과 상태를 조합한다. 기본 월, query 정규화, `replace`, 투자자 변경 시 재계산을 테스트하고, 재사용 가능한 URL helper 또는 feature component 추출이 실제 단순성을 높이는 경우만 추출한다.
- Dashboard: 탭 전환, 조회 조건, 요약/표 렌더링의 소유권을 조사한다. 페이지는 route 조합, feature는 사용자 작업 흐름을 소유한다. `features/dashboard`와 `entities/dashboard`의 기존 조회모델/키 배치를 유지한다.
- `useSaleForm`: OCR, 청약/배정 선택, 계산, 제출 상태를 조사한다. 순수 파싱·계산은 부수효과와 분리해 단위 테스트 가능하게 한다. 여러 hook으로 나누는 것 자체를 목표로 삼지 않는다.
- `MainLayout`: 로그인/게스트 네비게이션, 계좌/공지/로그아웃 상태를 확인한다. 이미 정해진 게스트 홈 동작을 포함해 사용자 경로를 보존하고, 메뉴 모델·행동이 독립적으로 복잡할 때만 추출한다.

**완료 기준**: URL과 사용자 흐름이 유지되고, 로딩·오류·빈 화면·키보드 접근성 테스트가 유지되며, 추출된 책임의 입력/출력과 공개 API가 작고 명확하다. 폴더 이동만 하는 PR은 만들지 않는다.

### 6.3 투자내역 조회 서비스

대상은 `InvestmentHistoryQueryService`, 관련 assembler/repository/DTO 및 테스트다. 읽기 전용 `InvestmentHistoryReadModelLoader` 같은 배치 로더와 DTO assembler 분리를 후보로 검토한다.

1. 현재 조회 분기, 주입 의존성, 쓰이지 않는 의존성, SQL/repository 호출과 소유권 필터를 목록화한다.
2. 구독 페이지와 상세 조회의 공유 규칙(인증 계정 범위, 삭제 행 제외, investor/account/IPO 데이터 조합)을 한 번만 정의하되 API 응답은 유지한다.
3. 배치 단계에서 subscriptions, allocations, sales 및 관련 IPO/investor/account/firm 정보를 수집하고, assembler는 미리 모은 데이터로 DTO만 조립하도록 후보를 좁힌다.
4. 레코드당 repository 호출이 생기지 않도록 쿼리 수 검증을 추가한다. 필터, 페이지네이션, 정렬 및 권한 검사 순서를 고정한다.
5. projection은 측정된 성능·메모리 이득이 있고 유지 비용을 정당화할 때만 검토한다.

**위험/금지**: 편의상 service를 여러 클래스로 쪼개며 호출을 늘리는 일, 계정 조건이 빠진 보조 조회, 페이지별 결과 변화, N+1, 논리 삭제 데이터 노출.

**완료 기준**: 계약 테스트 통과, 조회 쿼리 수가 기록된 기준선보다 비례해 증가하지 않음, 기준 데이터에서 응답 결과 동일, 수행 시간/계획이 설명 가능하다.

### 6.4 IPO 동기화 서비스

대상은 약 647 LOC로 확인된 `IpoSyncService`와 동기화 관련 client/repository/merge service/tests다. 후보 책임은 run coordinator, DART 공시/회사 발견, filing import, persistence, 일정 merge, 실행 결과·상태 기록이다. 실제 분리 단위는 characterization 후 결정한다.

1. `runExclusively`, 월 범위 처리, 회사별 처리, 공시·일정 병합, 기존 IPO 생성/갱신 경로를 흐름도로 기록한다.
2. 중복된 월/일 반복 경로가 실제로 같은 의미인지 비교하고, 같을 때만 공통 흐름으로 합친다.
3. 외부 fetch/파싱과 저장/트랜잭션을 분리한다. 외부 네트워크 호출 동안 DB transaction을 열어두지 않는다.
4. company 단위 오류, 전체 실행 중단 조건, 부분 성공, 재시도 정책과 결과 기록을 각각 테스트한다.
5. 동시 실행을 막는 단일 프로세스 lock의 범위와 획득/해제/예외 동작을 유지한다. 수동 입력 일정은 자동 동기화가 덮어쓰지 않아야 한다.
6. 비공식 일정 source 실패 시 OpenDART 기반 데이터까지 불필요하게 무효화하지 않는 현 동작을 확인·보존한다.
7. 스케줄러/관리자 서비스의 외부 인터페이스를 유지하고, 각 client가 오류를 명시적으로 반환하도록 한다.

**완료 기준**: 기존·부분 성공·외부 실패·회사 실패·재시도·동시 실행·수동 일정 보존 테스트가 모두 있고, 외부 호출 없이 재현 가능하다. DB transaction 경계가 테스트/코드에서 확인된다.

### 6.5 도메인 서비스 경계

후보는 `SecuritiesAccountService`, `SubscriptionService`, `AllocationService`, `SaleService`다. 먼저 규칙 위치와 중복을 맵핑하고, 한 번에 한 도메인만 다룬다. 정책/명령/조회 분리는 불필요한 레이어를 늘리지 않는 범위에서 적용한다.

- Securities account: 소유권 검증, 기본 계좌 정책, 계좌번호 암호화/마스킹, 등록/수정/비활성화 책임을 살핀다. 마스킹 누락이나 계정 간 접근을 막는 테스트를 분리 변경의 전후에 둔다.
- Subscription: 투자자·계좌·IPO 소유권, 동일 투자자+계좌+IPO 조합의 중복 방지, 금액/수량·상태 규칙을 한 경계에서 추적 가능하게 한다. 계좌가 다른 합법적 복수 청약은 보존한다.
- Allocation: 청약 소유권과 청약 수량 상한, 중복 배정, 배정 수량 0 허용 규칙을 보호한다.
- Sale: 배정 소유권, 활성 매도 합계가 배정수량을 넘지 않는 불변식, 수정 시 자기 자신 제외와 논리 삭제 규칙을 보호한다.
- `AnnouncementService`가 공통 `ApiException`/`GlobalExceptionHandler` 대신 `ResponseStatusException`을 직접 쓰는 점은 별도 후보로 기록하되, 우선순위는 낮게 둔다. API 상태/본문 계약 테스트 없이 예외 체계를 바꾸지 않는다.

**완료 기준**: 규칙별 테스트 이름과 소유 모듈이 명확하고, 기존 403/404 및 validation 응답, 기본 계좌, 0주, 중복·수량 경계가 유지된다. 새 DB 테이블/마이그레이션은 없다.

### 6.6 검증 자동화와 품질 경계

먼저 CI와 스크립트가 이미 실행하는 항목을 확인해 중복 게이트를 만들지 않는다. 필요한 경우 다음을 점진 적용한다.

- UI: 단위/컴포넌트 테스트, lint, typecheck, format check, build. API 연동 사용자 흐름은 기존 E2E 기반을 활용하고, 핵심 전이(미청약/청약완료/부분 결과/배정/0주 미배정)를 보강한다.
- Backend: 관련 테스트, 전체 테스트, `checkstyleMain`, `checkstyleTest`, `spotlessCheck`, build. 변경이 없거나 비용이 큰 검증은 단계별 SF 범위에 맞춰 실행하고 결과를 명시한다.
- 계약 테스트: JSON nullable·정렬·오류·소유권. E2E는 서로 다른 사용자 계정 데이터가 섞이지 않는지 확인한다.
- 접근성: 기존 키보드/role/레이블 규칙과 E2E 접근성 스캔을 유지한다. 무관한 전체 접근성 정리와 묶지 않는다.
- 성능: 조회 쿼리 수, 대표 데이터 규모, 응답시간 기준선을 기록한다. 수치 임계값은 반복 가능한 측정 근거가 생긴 뒤 설정한다.
- 경계 테스트: 이미 있는 FSD boundary 테스트를 유지한다. Backend 모듈 경계 테스트는 패키지 규칙과 현재 빌드 도구로 유지비가 낮을 때만 추가한다.

## 7. 제안 PR 순서

아래는 후보 순서다. Phase 0에서 이미 충족된 단계는 건너뛰고 근거를 남긴다. 각 PR은 하나의 사용자/API 계약을 보호하면서 독립적으로 병합 가능해야 한다.

1. 현재 계약·테스트·query key·Zod·취소·무효화의 gap matrix 작성 및 필요한 누락 테스트 보강.
2. 실제 미검증 API 응답만 Zod 경계로 보완하고 타입을 `z.infer`로 단일화.
3. `/subscriptions` 날짜 URL 상태 검증/정규화와 순수 유틸 테스트를 정리. 재사용 추출은 근거가 있을 때만.
4. DashboardPage 조합 책임을 필요한 범위에서 줄인다.
5. 투자내역 배치 read-model loader와 DTO assembler 경계를 정리한다.
6. IPO sync의 외부 수집/병합/저장/실행 상태 기록을 단계적으로 분리한다.
7. 계좌 및 청약·배정·매도 핵심 정책을 도메인별로 정리한다.
8. CI/계약/E2E/성능 회귀 기준을 gap 기준으로 보완하고 문서·handoff를 갱신한다.

3·4번은 중복/복잡성 근거가 없으면 생략한다. 5~7번은 계약 기준선과 특성화 테스트가 먼저다. 동시에 같은 계약/파일을 바꾸는 PR을 열지 않는다.

## 8. 리스크와 대응

| 리스크 | 대응 |
|---|---|
| 계획에 이미 완료된 Epic 20 규칙을 결함으로 오인 | 현재 `guide-frontend.md` 및 코드·테스트 대조, gap만 수정 |
| 서비스 분리 중 API JSON/오류가 달라짐 | MockMvc 계약 및 DTO fixture를 먼저 고정, 변경 diff 검토 |
| 투자내역 조회가 느려지거나 N+1 발생 | 배치 로딩 유지, 쿼리 수/응답시간 기준선 비교 |
| 동기화 중 외부 실패가 전체 데이터 손상으로 확대 | 외부 통신 mock, 회사/전체 실패 분리, 수동 일정 보존 테스트 |
| 계좌/투자자 보안 경계 누락 | 소유권 403/404·마스킹·민감 로그 테스트를 변경 단위에 포함 |
| 범위가 커져 재작성으로 변질 | 한 SF/한 관심사, 작은 병합, 추상화는 실측/중복 근거 필요 |
| 로컬 DB 자격 증명 혼선 | `.envrc`의 개발용 `DB_PASSWORD` 사용, Docker/배포 환경값 사용 금지 |
| 테스트가 외부 계정·API에 의존 | fake/mock 및 격리 DB 슬롯 사용, 실계정 호출 금지 |

제품 계약, 보안, 데이터, 비용에 중대한 미해결 결정이 생기면 임의로 정하지 않는다. 근거와 선택지를 문서화하고 프로젝트 지침의 승인 gate를 사용한다.

## 9. 검증 명령과 환경

명령은 작업 시작 시 `package.json`, Gradle 설정, 프로젝트 가이드에서 다시 확인한다. 아래는 기준 시점의 계획용 명령이며 비밀번호 값을 포함하지 않는다.

### UI

```bash
cd ui
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
```

### Backend

프로젝트 DB 슬롯 절차에 따라 격리된 개발 DB를 사용한다. `.envrc`가 로컬 환경변수를 안전하게 주입하는 방식으로 실행하고, 필요한 경우 `DB_NAME=ipo_slot_4`와 `SPRING_DATASOURCE_PASSWORD` 매핑을 프로젝트 설정에 맞춘다. 비밀번호를 명령행 인자나 로그에 출력하지 않는다.

```bash
DB_NAME=ipo_slot_4 ./gradlew test --rerun
./gradlew checkstyleMain checkstyleTest --rerun-tasks -q
./gradlew spotlessCheck
./gradlew build
```

실제 테스트 실행 시에는 사용 중인 슬롯과 DB 컨테이너 상태를 확인하고, 다른 작업자 데이터가 있는 슬롯을 초기화하지 않는다. 기본 AGENTS의 DB 슬롯 지침이 우선한다. 배포 DB와 운영 데이터는 사용하지 않는다.

## 10. Definition of Done

- 계획에서 다룬 모든 변경이 기존 API·URL·권한·도메인 불변식과 대조돼 있다.
- 관련 characterization/회귀 테스트가 있으며, 실행한 검증은 `PASS` / `FAIL` / `NOT_RUN` / `BLOCKED`로 사실대로 기록돼 있다.
- UI query/schema/public API와 Backend DTO/error/ownership 경계가 각 가이드에 맞는다.
- 투자내역 배치 조회와 IPO 동기화 실패/트랜잭션/락 의미가 보존된다.
- 새 스키마/외부 연동/사용자 기능이 승인 없이 추가되지 않았다.
- 실제 diff가 계획 범위만 포함하며, 필요한 work-log, breakdown, current-status와 일일 로그가 프로젝트 지침에 따라 갱신됐다.
- 완료된 SF의 변경을 Master가 직접 검토하고 테스트/빌드 증거를 확인했다. 워커의 완료 보고만으로 완료 처리하지 않는다.

## 11. 다음 담당자의 구체적인 시작 순서

1. `git status --short`, `git rev-parse HEAD`, 원격 main 및 열린 PR/이슈를 확인해 기준 커밋이 여전히 유효한지 기록한다.
2. `docs/current-status.md`와 `docs/roadmap.md`를 읽고 공지사항 Epic 등 진행 중인 변경과 충돌 여부를 확인한다.
3. `guide-frontend.md`와 `guide-backend.md`를 현재 코드에 대조한다. 특히 query key, AbortSignal, Zod, invalidation, 재시도, API 오류 계약은 이미 구현됐을 가능성이 크므로 실제 공백만 목록화한다.
4. `IpoSyncService`, `InvestmentHistoryQueryService`, `SecuritiesAccountService`의 현재 테스트·사용처·의존성·쿼리 흐름을 확인해 이 문서의 출발점 표를 갱신한다.
5. Phase 0 계약/테스트/성능 gap matrix를 `docs/research/`에 작성한다. 이 work-plan 문서를 조사 결과로 대체하거나 덮어쓰지 않는다.
6. 선택 트랙마다 spec과 architecture를 갱신하고, 범위·비범위·수용 기준·보안/성능 위험·테스트를 Master 승인에 제출한다.
7. 승인된 트랙만 3~8 SF로 breakdown하고 의존성을 task DAG로 만든다. 파일 소유권과 검증 명령을 각 task에 적는다.
8. 작게 구현하고 독립 PR로 검증한다. 중단 시 이 문서의 11절과 각 Epic work-log/current-status에 마지막 완료 SF, 수정 파일, 검증 상태, 다음 행동을 남긴다.

## 12. 작업 기록 표

실행이 시작되면 아래 표를 갱신하거나 Epic별 work-log로 옮기되, 상태 값은 임의로 생략하지 않는다.

| 항목 | 상태 | 근거/링크 | 다음 행동 |
|---|---|---|---|
| 기준 커밋과 작업 트리 확인 | PASS | 기준 `5855945a`, 현재 작업 `main`; 미커밋 변경 보존 | 다음 작업 시작 때 기준 재확인 |
| API·UI 계약 gap matrix | PASS (정적 대조) | [계약 matrix](../work-log/application-refactoring-contract-matrix-2026-10-26.md); GET/body mutation/query key/invalidation/secret cache/no-body 경계를 현재 소스와 대조 | 전체 UI 검증에서 회귀 테스트 실행 |
| 투자내역 조회 성능 기준 | NOT_RUN | 재현 가능한 측정 결과 없음 | 최종 검증 단계에서 대표 데이터 규모와 query count/응답 시간 수집 |
| IPO 동기화 실패 정책 기준 | PARTIAL | 일반·월 범위 테스트의 인증/020/일반 API 오류, 회사 실패, 수동 레코드 보존 및 병합 정책 확인; persistence collaborator 분리 | 전체 backend suite 및 transaction 동작 검증 |
| 리팩토링 트랙 승인 | PASS | 사용자가 리팩토링 시작 및 지속 진행을 명시 승인 | 다음 SF마다 범위·AC 문서화 유지 |



## styled-components 전환

### 목표와 완료된 코드 변경

- 프론트엔드 사용자 요청에 따라 앱 소유 CSS Module을 styled-components로 전환하고 시각 시스템을 정돈한다.
- `ui/package.json` / `pnpm-lock.yaml`: `styled-components` 6 의존성 추가.
- `ui/src/app/styles/GlobalStyles.ts`: design tokens, reset, 공통 폼/표/접근성 기본 스타일을 `createGlobalStyle`로 관리한다. `main.tsx`에서 Pretendard 패키지의 font stylesheet와 앱 소유 `GlobalStyles`를 렌더한다. 각 slice 스타일은 소유 컴포넌트가 직접 렌더한다.
- 각 page/widget/feature slice의 `*.styles.ts`가 해당 slice의 스타일을 소유한다. 컴포넌트는 default `styles` class map과 named `StyleScope`를 같은 co-located module에서 가져와 반환 UI를 `StyleScope` 안에 둔다. app이 상위·하위 slice 스타일을 전부 import하는 registry는 없다. 공통 스타일은 shared component 자체에 styled 정의한다.
- 새/교체된 공통 요소: styled-components 기반 `Button`, `Primitives.tsx`의 `SurfaceCard`, `SectionHeading`, `Eyebrow`, `StatusPill`; EmptyState/ErrorMessage/Spinner도 실제 styled component로 구현했다.
- Dashboard metric, landing hero, MainLayout의 표면·간격·상태 계층을 개선했다. 달력 grid 및 IPO insight bar의 dynamic values를 transient styled props로 바꿨다.
- 스타일 가이드는 `docs/guide-frontend.md` section 8로 갱신했다.

### 다음 담당자가 우선 확인할 것

1. `git status --short`로 이전 리팩토링과 이번 스타일 변경이 함께 남아 있는지 확인한다. 기존 수정은 되돌리지 않는다.
2. `*.styles.ts`는 해당 page/widget/feature/shared slice에 co-locate한다. 컴포넌트는 같은 파일에서 `styles`와 `StyleScope`를 가져오며, app-wide registry는 두지 않는다.
3. allocation/sale `ReferenceQuantity` 스타일은 각 소유 feature 안에 둔다. 제거된 CSS module, 미사용 스타일 파일, 빈 디렉터리 및 `shared/styles` 잔존 여부: `PASS` — 62개 스타일 파일 모두 사용 중이며 잔존 디렉터리/앱 소유 CSS 파일 없음.
4. UI 검증: `pnpm run test` 99개 파일/745개 통과, `lint`, `typecheck`, `format:check`, `build` 모두 PASS.
5. 브라우저 viewport 수동 확인(640px/768px/1024px), keyboard focus/reduced motion, hosted workflow와 E2E는 추가 확인할 항목이다: `NOT_RUN`.
6. 백엔드 Checkstyle·Spotless·build: PASS. `spotlessApply`는 실행하지 않고 지적된 포맷 네 파일을 수동 수정했다. backend test 1,101개 통과.
7. 최종으로 `git diff --check`, 전체 diff, current-status/daily-log/handoff를 갱신한다. hosted workflow와 실제 브라우저 E2E를 실행하지 않았다면 `NOT_RUN`으로 남긴다.

### 현재 검증 상태

- 최종 UI 테스트 99개 파일/745개, lint, typecheck, format:check, build: `PASS`.
- 최종 backend 테스트 1,101개, Checkstyle, Spotless, build: `PASS`.
- `git diff --check`: `PASS`. 브라우저 수동 viewport 확인과 hosted workflow/E2E: `NOT_RUN`.
