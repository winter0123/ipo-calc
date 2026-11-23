# 분해: KRX 추가상장 공고 기반 상장일·시초가 정합성 (krx-listing-reconciliation)

> Phase 0 승인 후 작성. 제품 코드 변경 없음. Epic slug: `krx-listing-reconciliation`.

## 1. 범위

기존 past `ESTIMATED_DART` 행만 대상으로 한다. IPO 행을 합성하지 않는다. 기존 명시적 수동 편집은 우선한다. KRX 증거는 참조 하나만 최소로 추가한다.

## 2. SF 목록

### SF-1 날짜 우선순위와 stale quote 정리

- 상태: ready (`task_0f89b22844ba`)
- 의존성: 없음
- 목적:
  - `mergeSupplementalSchedule`가 `CONFIRMED_KRX` 상장일을 덮지 않게 한다.
  - 청약일 이전 날짜를 상장일로 수용하지 않는다.
  - 날짜 또는 종목코드 변경 시 `listing_open_price`를 null로 초기화한다.
  - DART 예정일만 quote 기본값에서 제외한다.
  - 동일 날짜/코드/행 조건에서만 quote를 저장한다.
- 주요 파일:
  - `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java`
  - `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoScheduleMergeService.java`
  - 관련 테스트: `IpoScheduleMergeServiceTest`, `IpoTest`
- AC:
  - `CONFIRMED_KRX` 행의 상장일이 보조 일정으로 변경되지 않음
  - 청약일 이전 날짜 거부
  - 날짜/코드 변경 시 `listing_open_price == null`
  - 같은 날짜 재적용 시 `listing_open_price` 보존
  - DART estimate만 quote에서 제외
- 테스트 명령:
  - `DB_PASSWORD=ipo_local_password ./gradlew test --tests "*Ipo*" --tests "*IpoScheduleMergeService*" --rerun`

### SF-2 공식 KIND 미리보기·적용 + KRX 증거

- 상태: SF1 이후
- 의존성: SF-1
- 목적:
  - 고정 공식 host/path/프로토콜 검사
  - redirect 차단
  - 관리자 권한 확인
  - 읽기 전용 미리보기
  - 동일 후보 재검증
  - 멱등 적용
  - DART 실적보고서 실제 발행주식수 매칭
  - KRX 증거 참조 하나 추가
- 주요 파일(예정):
  - 신규 `KrxListingNoticeClient`
  - 신규 `KrxListingNoticeParser`
  - 신규 `KrxListingReconciliationService`
  - 신규 관리자 controller/DTO
  - `Ipo.java` 최소 필드 추가(SF2에서 확정)
  - migration 초안(로컬 우선)
- AC:
  - 공식 host/path만 허용
  - redirect 차단
  - 종목코드·실제 발행주식수·사유·회차 불일치 시 거절
  - 무상/전환/다른 회차/다수 매치/고유 증명 불가 거절 또는 review-only
  - KRX 발행일과 DART 납입일은 보강 근거로만 사용
  - 같은 후보 재적용 멱등
  - invalid/missing/ambiguous 입력에 쓰기 0건
- 테스트 명령:
  - `DB_PASSWORD=ipo_local_password ./gradlew test --tests "*KrxListing*" --rerun`

### SF-3 캘린더/상세/목록 일관 상태 + 관리자 트리거

- 상태: SF2 이후
- 의존성: SF-2
- 목적:
  - 상세/목록 공통 상태 규칙
  - 출처 라벨 표시
  - SF2 미리보기/적용 관리자 UI 트리거
- 주요 파일(예정):
  - `ui/src/entities/ipo/*`
  - IPO 상세/목록 컴포넌트
  - admin UI 컴포넌트
- AC:
  - 동일 상태 규칙 사용
  - DART 예정일을 자동 완료로 표시하지 않음
  - 출처 라벨 표시
  - 관리자 트리거로 SF2 실행 가능
- 테스트 명령:
  - `pnpm test`
  - `pnpm typecheck`
  - `pnpm lint`
  - `pnpm build`

### SF-4 DART 실패 관측과 월별 연속 조회

- 상태: SF1 이후
- 의존성: SF-1
- 목적:
  - DART `013` empty와 실패 구분
  - 월 범위 연속 조회와 prior protection
  - 기존 typed convention 재사용
- 주요 파일(예정):
  - `OpenDartCorpDisclosureClient`
  - `OpenDartEquitySecuritiesClient`
  - `IpoSyncService`
  - 월 범위 테스트
- AC:
  - `013` empty와 실패 구분 관측
  - `020`, 인증, HTTP, null 실패 convention 유지
  - 월별 일반 상장월/유상 청약월 분리
  - prior protection 유지
  - SF2와 코드 범위 중복은 합의 없이 하지 않음
- 테스트 명령:
  - `DB_PASSWORD=ipo_local_password ./gradlew test --tests "*IpoSync*" --tests "*OpenDart*" --rerun`

### SF-5 읽기 전용 41건 과거 검증과 로컬 E2E

- 상태: SF1, SF2, SF3, SF4 이후
- 의존성: SF-1, SF-2, SF-3, SF-4
- 목적:
  - 기존 past `ESTIMATED_DART` 41건 검증
  - 생산 데이터는 읽기만 수행
  - 로컬 E2E 테스트
- 주요 파일(예정):
  - 검증 스크립트/테스트
  - 로컬 E2E 테스트
- AC:
  - 41건 검증 보고
  - 생산 데이터 쓰기 0건
  - 로컬 E2E 통과
  - invalid/missing/ambiguous 입력 쓰기 0건 유지
- 테스트 명령:
  - 로컬 테스트 전체 + E2E 명령은 Master가 지정

## 3. 공통 규칙

- Ambiguous input은 review-only 또는 no mutation이다.
- Idempotency는 모든 적용 경로에서 검증한다.
- invalid/missing/ambiguous 입력에 쓰기 0건이다.
- push, merge, deployment, production writes는 수행하지 않는다.
- DB migration 구현은 로컬 우선, 배포는 별도 승인 후 수행한다.


## 4. 2026-11-24 자동화 후속 SF 및 검증 게이트 (PR #515)

1. **SF-A 경로 연결:** 정기/수동 기본/월별 DART 성공 뒤 `KrxListingAutoSyncService` 호출. DART 중단·발행실적 실패 시 KIND 차단. 테스트: `IpoSyncSchedulerTest`, `IpoManualSyncServiceTest`.
2. **SF-B 공식 자동 발견:** 종목코드/게시일 검색→모든 페이지→제목 분류→뷰어→공식 원문. 대상 월/기간 한정, 정정·취소 및 페이지 실패 보류. 테스트: `KrxListingNoticeDiscoveryClientTest`, 수동 live test.
3. **SF-C 안전한 저장:** 신규상장 단일 후보 조건부 저장; 추가상장은 독립 발행회차 필요. 유효 1건+미확인 다른 1건도 쓰기 금지. 테스트: `KrxListingAutoSyncServiceTest`, PostgreSQL `KrxListingReconciliationServiceIntegrationTest`.
4. **SF-D 운영 출시 게이트:** 공식 KIND 응답 범위·정정 연결의 안정성, 실제 DART-KIND 발행회차 증명, 신규/추가 실서버 read-only 확인 후 자동 적용 활성화를 별도로 결정한다. 로컬 41건 스냅샷과 운영 브라우저는 검증 없이 PASS 주장 금지.

CI: 기존 Quality의 Backend checkstyle/spotless/build/동기화 테스트 + UI 품질. KIND live는 `workflow_dispatch` 전용이므로 PR run에서는 `SKIPPED`이며 공식 조회 PASS가 아니다. `git diff --check`는 GitHub만 접근 가능한 경우 별도 명령 실행 `NOT_RUN`으로 표기한다.
