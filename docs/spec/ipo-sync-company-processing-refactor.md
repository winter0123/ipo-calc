# IPO 회사별 동기화 처리 통합 Spec

## 목표

최근 N일 경로와 월 범위 경로의 중복된 회사 단위 검증·저장 흐름을 하나로 합쳐 장애 정책이 분기별로 달라지는 위험을 줄인다.

## 범위 / 비범위

- 범위: `IpoSyncService` 내부 회사 처리, 필요한 함수형 fetch 전달, 관련 회귀 테스트 및 문서.
- 비범위: public sync API, lock·scheduler·월 반복, 전체 동기화 결과/상태 저장, 외부 client 계약, IPO 저장 규칙, API/DB schema.

## Acceptance Criteria

1. 최근 N일과 월 범위가 동일한 회사 검증·최신 상세 선택·건별 저장 함수를 사용한다.
2. 각 경로가 원래 DS006 조회 client 메서드와 원래 기간 인자를 유지한다.
3. stock_code 제외, 상세 없음, 날짜 파싱/역전, 최신 접수번호, 회사별 실패, 인증/020 중단의 결과가 기존과 같다.
4. 회사별 transaction/rollback, 수동 IPO 보호, 자동 데이터 갱신, OpenDART 정상 완료 후 일정 병합이 유지된다.
5. 관련 테스트, Checkstyle, Spotless 및 backend build가 통과한다.

## 제약

공통화를 위해 추상 클래스/새 프레임워크를 추가하지 않는다. result 상태는 처리 순서와 함께 유지한다. 테스트에서 외부 OpenDART/ustockplus API를 호출하지 않는다.

## 후속 SF-2 — 결과 초기화 공통화

- `doSync`, `doSyncForMonth`, 빈 월 범위 반환이 중복 생성하는 같은 빈 `IpoSyncResult`를 private factory 하나로 만든다.
- `created`, `updated`, `skippedManual`, `skippedNonIpo`, `failureCount`는 0, `failures`는 새 리스트, `aborted`는 false로 유지하고 retryable/abortReason/scheduleMerged의 기존 기본값 의미를 바꾸지 않는다.
- 동기화 흐름·결과 정책은 변경하지 않으며 기존 빈 DS001/월 범위 테스트로 고정한다.

## 후속 SF-3 — IPO 동기화 영속화 경계 분리

- transaction 내부의 기존 IPO 조회, 수동 레코드 보호, 신규 생성 및 자동 레코드 갱신을 `IpoSyncPersistence`로 이동한다.
- 건별 transaction과 외부 API fetch/파싱은 `IpoSyncService`가 소유한다. persistence는 transaction이 열린 callback 내부에서만 호출한다.
- persistence는 생성/갱신/수동 보호 건너뜀 결과를 반환하고 coordinator가 이에 따라 기존 결과 카운터를 갱신한다.
- repository 조회 조건, 이름 정규화, 보존 규칙, 예외 처리 및 API/DB 계약은 유지한다.
