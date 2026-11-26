# 아키텍처: KRX 추가상장 공고 기반 상장일·시초가 정합성 (krx-listing-reconciliation)

> 기존 모듈 확장 중심. 설계 초안이며 DB/코드 변경을 실행하지 않는다.

## 1. 데이터 흐름

```text
DART 동기화
  → ipos.listing_date = DART 예정일
  → ipos.listing_date_evidence_type = ESTIMATED_DART
  → ipos.listing_date_evidence_receipt = DART receipt
  → ipos.lastImportedListingDate = DART 예정일

운영자
  → 관리자 화면에서 KIND 공고 URL/검색 조건 입력
  → 서버가 공식 host/path/프로토콜로 문서 조회(redirect 차단)
  → 파서가 종목코드, 상장일, DART 실적보고서 실제 발행주식수, 발행 사유, 발행 회차, 발행일 추출
  → 미리보기: 현재 DART 예정일 vs KRX 확정일 diff
  → 운영자 확인
  → 서버가 동일 후보 재검증
  → 트랜잭션 적용:
      listing_date = KRX 확정일
      listing_date_evidence_type = CONFIRMED_KRX
      lastImportedListingDate = 기존 DART 예정일 보존
      listing_date_evidence_receipt = 기존 DART receipt 보존
      KRX 증거 참조 하나 추가
      listing_open_price = (날짜/코드 변경 시 null, 아니면 보존)
  → mergeSupplementalSchedule은 CONFIRMED_KRX 행을 건너뜀
  → 시초가 배치는 확정일 기준 대상만 수집
```

## 2. 모듈 경계

- `ipo-catalog`: `Ipo`, `IpoService`, `IpoScheduleMergeService`, 시초가 배치
- `ipo sync`: DART 클라이언트/서비스, 기존 typed 실패 convention
- 신규 reconciliation 서비스(가칭): `KrxListingNoticeClient`, `KrxListingNoticeParser`, `KrxListingReconciliationService`, 관리자 controller
- UI: IPO 타입/상태 유틸, 상세/목록, 관리자 미리보기

신규 모듈은 기존 `HttpClientSupport`, 인증/권한, DTO 패턴을 재사용한다.

## 3. 최소 스키마 (SF2에서 확정)

기존 컬럼을 재사용한다.

- `listing_date_evidence_type`: `ESTIMATED_DART`, `CONFIRMED_KRX`, null
- `listing_date_evidence_receipt`: DART receipt
- `last_imported_listing_date`: DART 원래 예정일 보존
- `listing_open_price`: 시초가

KRX 증거는 별도 참조 하나만 최소로 추가한다. 이름은 SF2에서 확정한다. raw HTML은 저장하지 않는다. shadow listing date, duplicate audit table, multiple state columns는 만들지 않는다.

## 4. 매칭 규칙

적용 전 다음을 모두 확인한다.

1. 종목코드가 내부 IPO의 `stock_code`와 정확히 일치한다.
2. 공고의 실제 추가주식수가 DART 실적보고서의 실제 발행주식수와 일치한다.
3. 발행 사유가 유상증자 계열이다.
4. 발행 회차가 내부 근거와 일치한다.
5. 매치 후보가 정확히 1건이다.
6. DART 결과가 matched row로 특정 가능하다.

거절 또는 review-only 조건:

- 무상증자
- 전환/행사
- 다른 발행 회차
- 다수 매치
- 종목코드 또는 수량 불일치
- 고유 증명 불가
- 공고 파싱 실패

## 5. 보호 규칙

- `mergeSupplementalSchedule`는 `CONFIRMED_KRX` 상장일을 덮지 않는다.
- 보조 일정 정정은 추적값이 현재값과 같을 때만 한다.
- 수동 날짜 변경은 기존 추적 필드를 무효화한다.
- 상장일 또는 종목코드가 바뀌면 `listing_open_price`를 null로 바꾼다.
- 같은 날짜 재적용에서는 `listing_open_price`를 보존한다.
- DART 원래 날짜/receipt는 KRX 확정 후에도 유지한다.
- 기존 명시적 수동 편집은 사용자 작업으로 우선한다.

## 6. 시초가 배치

- 대상: KRX 확정 상장일(또는 명시적 수동 확정일)과 종목코드가 있는 행, `listing_date <= 어제`, `listing_open_price is null`
- DART 예정일만 있는 행은 quote에서 제외한다.
- 레거시 null/eligible 행의 기존 수집은 유지한다.
- 기존 멱등 UPDATE 방식을 유지한다.
- 날짜/코드 변경 시 null로 초기화되어 다음 배치가 재수집한다.

## 7. DART 실패 관측

기존 typed convention을 재사용한다.

| 상황 | 기존 처리 |
|---|---|
| `013` | 데이터 없음, empty 반환 |
| `020` | 요청 제한, `OpenDartApiException` 실패/중단 |
| `010/011/012/901` | 인증 오류, `OpenDartAuthenticationException` |
| HTTP 호출 실패 | `OpenDartApiException("http_error", ...)` |
| null 응답 | `OpenDartApiException("null", ...)` |

SF4는 `013` empty와 실패를 구분해 관측한다. 새 실패 enum을 만들지 않는다.

## 8. UI 공통 표시

- 상세와 목록은 같은 상태 유틸을 사용한다.
- 상태는 상장일 출처와 날짜 기준으로 계산한다.
- DART 예정일이 과거라고 해서 자동으로 "상장 완료"로 표시하지 않는다.
- 출처 라벨(`DART 예정`, `수동`, `KRX 확정`)을 표시한다.
- 월별 필터는 `listingMonth`와 `subscriptionMonth`를 분리한다.
- KRX 발행일과 DART 납입일은 구분해 표시한다.

## 9. 보안

- 입력 서버는 고정 공식 host와 허용 path만 사용한다.
- redirect를 차단한다.
- 관리자 권한을 요구한다.
- 공식 host/path/프로토콜 검사를 수행한다.
- 요청 크기/timeout을 제한한다.
- 로그에 민감정보를 남기지 않는다.
- raw HTML을 저장하지 않는다.

## 10. 동시성/멱등

- 적용은 트랜잭션으로 수행한다.
- 같은 공고/후보 재적용은 no-op으로 처리한다.
- 동시 적용은 DB 락 또는 유니크 제약으로 직렬화한다.
- invalid/missing/ambiguous 입력에 대해 쓰기 0건을 보장한다.
- 부분 실패 시 실패 사유를 기록하고 재시도 가능 상태로 남긴다.

## 11. 테스트 전략

- 파서: 4개 KIND 공고 fixture 기반 테스트
- 매칭: 종목코드/실제 발행주식수/사유/회차/다수 매치/고유 증명 불가 거절 테스트
- 보호: `mergeSupplementalSchedule` KRX 불변 테스트
- 시초가: 날짜/코드 변경 시 null, 같은 날짜 보존 테스트
- DART: `013` empty와 실패 구분 테스트
- UI: 공통 상태, 출처 라벨, 월 분리 테스트
- 네트워크: 실제 KIND 호출 금지, fixture/mock만 사용

## 12. 미결정 사항

- KRX 증거 참조 컬럼명 최종 확정
- 관리자 화면 위치와 권한 세부
- DART 원본 날짜/receipt를 UI에 노출할지
- 공식 추가상장 OpenAPI 부재에 대한 장기 공급 계약
- 배치 자동화는 별도 Epic으로 분리할지


## 13. 2026-11-24 운영 경로의 자동화 구조 (PR #515)

```text
IpoSyncScheduler.runSync (DART sync 성공 → rights.backfill failures=0)
IpoManualSyncService.runDefault / runRangeSync (동일한 DART 선행 조건)
  → KrxListingAutoSyncService (auto-apply-enabled=false 기본; 범위별 IPO 후보)
  → KrxListingNoticeDiscoveryClient.find (종목코드·게시일, bounded pagination)
  → 제목 분류/수정·취소/후보 수 판정
  → disclsviewer 접수번호→70760/70791 URL → KrxListingNoticeClient
  → KrxListingReconciliationService.previewNewListing / preview
  → 단일 후보·독립 회차 근거 확인 시 applyNewListing / apply
  → IpoRepository 조건부 UPDATE (원천 접수번호·현재 날짜·증거·상태 재검증)
  → OutcomeType별 건수 및 행별 사유 집계
```

네트워크/파싱은 트랜잭션 밖에 두고 저장 시점에 현재 행을 비교한다. 동일 공고 `NO_OP`, 날짜 변경 시 시초가 null, 다른 확정 근거와 수동 수정 보호. KIND HTTP 403, 페이지 누락, 형식 변경은 `FETCH_FAILED`로 실패 폐쇄. 근거 없는 추가상장·정정·취소는 `REVIEW_REQUIRED`이고 임의 확정하지 않는다.

**남은 설계 한계:** 자동 추가상장 경로는 내부 `KrxListingApplyRequest` DTO를 이용해 기존 서비스 검증·조건부 저장을 재사용한다(관리자 HTTP 호출은 아님). 관리자 DTO와 도메인 적용 계약 분리는 별도 리팩터링 대상. 공개 KIND HTML 공급의 포괄성·장기 안정성 미보증, DART-KIND 실제 4건의 독립 회차 연결 증거 없음. 자동 적용 기본값 `false`이므로 안전성 및 운영 공급 경로 검증 없이 설정을 켜서는 안 된다.

## 자동 탐색·적용 경로 (2026-11-24)

```text
IpoSyncScheduler.runSync (04:30) / IpoManualSyncService.syncNow·syncRange
  → 기존 DART 목록·보완 완료 (중단 없음, rights.failures == 0)
  → KrxListingAutoSyncService.reconcileRecent / reconcileSubscriptionMonths
  → KrxListingNoticeDiscoveryClient.find(종목코드, 게시일 창)
  → 공고 제목 유형 분리, 여러 페이지·정정/취소/후보 유일성 확인
  → 접수번호로 공식 KIND 뷰어에서 70760(신규) 또는 70791(추가) 원문 해석
  → KrxListingReconciliationService.previewNewListing / preview
  → applyNewListing / apply (외부 HTTP는 DB 잠금 전에 수행)
  → IpoRepository 조건부 UPDATE (CONFIRMED_KRX, 공식 URL, 원 DART 참조 보존)
```

- 실패를 `FETCH_FAILED` / `REVIEW_REQUIRED` / `ROUND_CONFLICT` / `NO_NOTICE`로 구분하고 종목별 실패를 격리한다. **추가상장에 하나라도 근거 불충분한 공고가 섞이면** 다른 적격 공고도 자동 확정하지 않는다.
- 후보 결과는 게시일 28일 이하 창으로 탐색하며 최대 페이지 50, 페이지 실패 시 전체 종목을 조회 실패로 처리한다. 반복 공고는 접수번호로 중복 제거한다. 스키마 증설 없이 V49 기존 근거 필드를 사용한다.
- 외부 KIND API/실서버 검색의 안정성은 배포·실서버 자동 저장 활성화와 별도로 검증해야 한다. 현재 기본 flag false이며 관리자 API/UI는 예외 처리 경로로 유지한다.

## 2026-11-26 자동 적용 안전성 보완 (#515)

- 별도 낮 `scanDuringDay` 예약은 제거했다. DART 선행 검증이 포함된 기존 정기·기본·월별 동기화만 자동 적용을 시작한다.
- 자동 추가상장은 관리자 HTTP DTO 없이 서비스 내부 `applyAutomatically`를 사용하며 관리자 `apply`와 동일한 `applyVerified` 트랜잭션/조건부 갱신을 공유한다. 검색 당시 청약 시작일은 저장 시 행 잠금 아래 다시 비교한다.
- 공시 목록에서 열이 부족한 데이터 행은 버리지 않고 전체 검색을 실패 처리한다. 짧은 페이지 직후 명시적인 빈 페이지를 확인하지 못하면 후보 전체를 보류한다. 사이트 전체 결과 수·정정 원공고 관계에 대한 공식 종료 계약이 확인되지 않은 범위는 운영 자동 적용 승인 대상이 아니다.
- 관리자 자동 상태 UI는 플래그 false를 실행 중인 검증 상태와 혼동하지 않도록 명시적으로 비활성으로 표시한다.
