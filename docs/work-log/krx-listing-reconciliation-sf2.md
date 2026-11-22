# work-log: KRX 추가상장 정합성 SF2 최종 보완 (task_012e212f3815)

> 코디네이터 추가 지시(msg_2b09df6928f3 포함) 이행 기록. SF1/SF4/운영/원격은 건드리지 않음.

## 1. 구현 변경

| 파일 | 변경 |
| --- | --- |
| `controller/ipo/dto/KrxListingApplyRequest.java` | 하위 호환 생성자 2개와 별칭 메서드 4개 제거. 기대값 8개를 받는 canonical record만 유지. |
| `controller/ipo/KrxListingReconciliationController.java` | apply가 요청 DTO 전체를 단일 API로 전달. |
| `service/ipo/krx/KrxListingReconciliationService.java` | apply 오버로드 제거하고 `apply(Long, KrxListingApplyRequest)` 단일 진입점. preview 7개 기대값(receipt/예정일/종목코드/발행주식수/발행가/KRX 상장일/공고 해시) 필수 비교, 불일치 409. DART 상장예정일 후보가 2건 이상이면 `loadDart`가 review-only 처리. |
| `domain/model/ipo/Ipo.java` | 사용되지 않는 비원자 `confirmKrxListing` 도메인 메서드 제거(조건부 UPDATE SQL만 갱신 경로). |
| `service/ipo/krx/KrxListingNoticeClient.java` | no-redirect 요청 팩토리에 connect 5s / read 10s timeout 추가. URL·1MB 제한 유지. |

원자 갱신은 기존 `IpoRepository.confirmKrxListingIfCurrent` 조건부 UPDATE WHERE가 담당한다.
조건: id, ACTIVE, 미삭제, 종목코드, ESTIMATED_DART, DART receipt, 현재 DART 예정일, 자동 추적 예정일(lastImportedListingDate).
DART 원래 날짜/receipt는 갱신하지 않고 보존한다.

## 2. 테스트 파일

- `backend/src/test/java/com/ipocalc/ipocatalog/service/ipo/krx/KrxListingReconciliationServiceIntegrationTest.java` (10건)
  - `appliesFourRealCasesAndIsIdempotent`: 실제 KIND/DART 4종목(koiz/amico/raon/vivo) replay, PostgreSQL apply 통과, receipt·원래 예정일 보존, 재시도 NO_OP
  - `previewDoesNotWriteAndExposesExpectedValues`: preview 읽기 전용 + 기대값 노출
  - `noticeUrlOnlyJsonIsRejectedAndDoesNotWrite`: URL만 있는 JSON은 null 기대값 → 409, 쓰기 0 (Jackson 역직렬화로 증명)
  - `stalePreviewDoesNotWrite`: preview 후 별도 트랜잭션 날짜 변경 → 409, 쓰기 0
  - `concurrentChangeAfterPreviewWritesZero`: 재검증 중 날짜 변경 → 409 + 조건부 UPDATE 0행 직접 확인
  - `concurrentIdenticalAppliesReportSingleAppliedAndNoOp`: 동시 요청 정확히 APPLIED 1 / NO_OP 1
  - `differentRoundNoticeStaysReviewOnlyAndDoesNotWrite`: 다른 회차 공고 REVIEW_ONLY
  - `sameListingDateButDifferentNoticeDocumentIsRejectedAndDoesNotWrite`: 날짜 같고 본문만 수정 → 해시 불일치 409
  - `ambiguousDartListingCandidatesStayReviewOnlyAndWriteZero`: DART 예정일 2후보 → REVIEW_ONLY, 쓰기 0
  - `conditionalUpdateWritesZeroWhenDateOrStockCodeChanged`: 날짜/종목코드 변경 시 CAS 0행
- `backend/src/test/java/com/ipocalc/ipocatalog/service/ipo/krx/KrxListingNoticeParserTest.java` (2건)
  - `parsesFourRealNotices`, `parsesDifferentRoundNoticeWithLaterRoundAndListingDate`
- fixture: `backend/src/test/resources/krx/notices/*.html` 5건, `backend/src/test/resources/krx/dart/*.xml` 4건 (classpath, 네트워크 호출 없음)

## 3. 실행 명령과 결과 (실제 출력)

```sh
cd backend
./gradlew spotlessCheck checkstyleMain checkstyleTest compileTestJava --console=plain
# BUILD SUCCESSFUL in 4s

DB_NAME=krx_sf2_test ./gradlew test --tests "*KrxListing*" --rerun --console=plain
# BUILD SUCCESSFUL in 9s
# KrxListingNoticeParserTest: tests=2 skipped=0 failures=0 errors=0
# KrxListingReconciliationServiceIntegrationTest: tests=10 skipped=0 failures=0 errors=0
```

`/Users/joy/orca/workspaces/ipo-calc/krx-listing-reconciliation`에서 `git diff --check`도 통과.
테스트 DB는 개발 컨테이너 안에 별도 생성한 `krx_sf2_test`를 사용해 기존 스위트와 격리했다.

## 4. 잔여

- 이번 범위 내 미해결 항목 없음. SF3(관리자 UI), SF4, 운영/원격 작업은 손대지 않음.
