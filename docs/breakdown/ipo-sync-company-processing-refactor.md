# IPO sync 회사 처리 Breakdown

## SF-1 — 최근 N일/월 범위 회사 처리 흐름 통합

### 범위

- 수정 후보: `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncService.java`와 두 기존 테스트(`IpoSyncServiceTest`, `IpoSyncServiceMonthRangeTest`).
- 회사 처리의 공통부만 하나로 합치고 DS006 호출 함수만 각 실행 경로에서 제공한다.
- `sync`와 `syncByMonthRange`의 public 메서드, run lock, 월 루프, 저장 규칙은 그대로 둔다.

### Acceptance Criteria

`docs/spec/ipo-sync-company-processing-refactor.md`의 모든 기준을 만족한다. 각 경로의 성공·실패 분류 및 중단 결과를 기존 테스트가 보호한다.

### 검증

우선 두 동기화 테스트 클래스를 실행하고, SF 완료 시 전체 backend test, checkstyleMain/checkstyleTest, spotlessCheck, build를 실행한다. 환경은 `.envrc` 개발용 DB 비밀번호와 격리된 DB slot만 사용한다. 외부 API는 mock 상태를 유지한다.

### 완료 보고

변경 파일, 두 sync 경로의 보존된 차이, 검증 결과 PASS/FAIL/NOT_RUN/BLOCKED, 잔여 복잡도를 work-log/current-status에 기록한다.

## SF-2 — 동기화 결과 초기화 factory 통합

### 범위

- 대상: `IpoSyncService`의 `doSync`, `doSyncForMonth`, `emptyResult`.
- 같은 초기 결과 필드를 private factory 하나에서 설정하고 세 경로가 이를 사용한다.
- `IpoSyncServiceTest.sync_emptyDs001Result_returnsEmptyResult`와 `IpoSyncServiceMonthRangeTest.syncByMonthRange_emptyCompanies_returnsEmptyResult`로 빈 결과를 보호한다.

### 비범위

회사 처리, API 호출, lock, 월 범위, error classification, transaction, 결과/스케줄 병합 순서는 변경하지 않는다.

### 검증

사용자 지시에 따라 실행은 마지막 누적 backend 검증 시 함께 수행한다: `NOT_RUN`.

## SF-3 — 영속화 책임 분리

### 범위

- `IpoSyncPersistence`를 추가하고 기존/수동 레코드 판별, 생성, 자동 레코드 갱신을 이동한다.
- transaction 경계와 결과 카운터 소유권은 `IpoSyncService`에 둔다.
- 기존 일반/월 범위 service 테스트의 fixture 구성만 새 collaborator에 맞춘다.

### Acceptance Criteria

- DART fetch가 완료된 뒤에만 건별 transaction이 열리고, 그 안에서 persistence가 호출된다.
- 기존 두 경로의 생성·갱신·수동 보호·중복 키 실패 결과가 유지된다.
- 결과 counter는 persistence outcome과 일치한다.

### 비범위

API, DB schema, repository 쿼리, 일정 병합, transaction 경계, 외부 client 계약 변경.

### 검증

일반/월 범위 sync 테스트 및 전체 backend 검증에서 확인한다. 사용자 지시에 따라 현재 실행은 보류한다: `NOT_RUN`.
