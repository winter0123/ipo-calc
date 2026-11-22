# 연구: KRX 추가상장 공고 기반 상장일·시초가 정합성 (krx-listing-reconciliation)

> 읽기 전용 조사. 코드·운영 데이터·DB·배포·원격 변경 금지. 기준일 2026-11-22.

## 1. 운영 공개 API 집계 (2026-11-22)

공개 `GET /api/ipos` 응답 기준이다. 원 API는 `syncSource`, `deletedAt`, `withdrawnReceiptNo`를 반환하지 않으므로 해당 값은 미확인으로 둔다.

- ACTIVE 공모주: 140건
- `ESTIMATED_DART`: 42건, 그중 과거 예정일: 41건
- `CONFIRMED_KRX`: 0건
- 출처 미지정: 68건
- 날짜 없음: 30건(청약 종료 9건)

운영 DB 직접 조회는 수행하지 않았다.

## 2. 코드 기준선

- 현재 main: `08bd3aeb` (PR #514 기준)
- 검증 기준: backend 157건, UI 147건 통과
- 기존 상장일 증거 컬럼: `listing_date_evidence_type` (`ESTIMATED_DART`, `CONFIRMED_KRX`, null)
- 기존 DART 근거: `listing_date_evidence_receipt`
- 기존 DART 실패 타입: `OpenDartApiException(status)`, `OpenDartAuthenticationException`

## 3. 확인된 동작 결함

1. `Ipo.mergeSupplementalSchedule`가 `CONFIRMED_KRX` 상장일을 덮을 수 있다.
2. 청약일 이전 날짜도 상장일로 수용한다.
3. 수동 날짜 변경 시 기존 시초가가 잔류할 수 있다.
4. 시초가 배치가 DART 예정일을 포함한다.
5. UI 상세/목록이 지난 DART 예정일도 완료로 표시한다.
6. DART `013`(데이터 없음)과 `020`(요청 제한), HTTP/JSON 오류를 구분하지 않는다.
7. 월 범위에서 일반 상장월과 유상 청약월을 혼용한다.

## 4. KRX 공식 공고 검증 4건

공식 KIND 추가상장 공고를 직접 조회했다. KRX 상장일과 KRX 발행회차의 발행일은 DART 납입일과 의미가 다르며 실제 하루 차이가 날 수 있다.

| 회사 | 단축코드 | KRX 상장일 | 발행회차(발행일) | 실제 추가주식수 | 추가 사유 | 비고 |
|---|---:|---|---|---:|---|---|
| (주)코이즈 | 121850 | 2026-02-10 | 제16회('26.01.30) | 1,995,582 | 유상증자(구주주배정) | 계획 3,000,000 vs 실제 1,995,582 |
| 아미코젠(주) | 092040 | 2026-02-26 | 제20회('26.02.12) | 14,925,374 | 유상증자(일반공모) | - |
| 라온피플 주식회사 | 300120 | 2026-03-10 | 제19회('26.02.26) | 14,500,000 | 유상증자(구주주배정 후 실권주 일반공모) | 무상증자 2026-03-24 별건 |
| (주)비보존제약 | 082800 | 2026-03-19 | 제47회('26.03.05) | 10,615,000 | 유상증자(구주주배정) | - |

공고 URL:

- https://kind.krx.co.kr/external/2026/02/05/001019/20260205002638/70791.htm
- https://kind.krx.co.kr/external/2026/02/23/000883/20260223002329/70791.htm
- https://kind.krx.co.kr/external/2026/03/05/001391/20260305003200/70791.htm
- https://kind.krx.co.kr/external/2026/03/16/001505/20260316004580/70791.htm

## 5. 공식 자료 접근성

- KIND 검색과 공개 문서 확인은 가능하다.
- 문서화된 추가상장 OpenAPI는 미확인이다.
- 단일 주식발행내역 목록에서 코이즈가 누락된 사례가 있다.
- 추정 근접 매칭의 자동 승인은 금지한다. 종목코드·DART 실적보고서의 실제 발행주식수·발행 사유·발행 회차가 모두 일치해야 한다.
- KRX 발행일과 DART 납입일은 구분한다. 일치 여부가 아니라 보강 근거로 사용한다.

## 6. DART 실패 관측

- `013`: 조회된 데이터 없음. 정상 empty 처리다.
- `020`: 요청 제한 초과. `OpenDartApiException("020", ...)`로 실패 처리하고 중단한다.
- `010/011/012/901`: 인증 오류. `OpenDartAuthenticationException`으로 처리한다.
- HTTP 호출 실패: `OpenDartApiException("http_error", ...)`.
- null 응답: `OpenDartApiException("null", ...)`.
- 기존 typed convention을 재사용하고 새 실패 enum을 만들지 않는다.

## 7. 공급 방식 미해결

- 공식 추가상장 OpenAPI는 문서화되어 있지 않다.
- 첫 구현은 검증 가능한 공식 KIND 문서를 입력으로 받는 운영자 경로로 제한한다.
- 공식 host/path/프로토콜을 고정하고 redirect를 차단한다.
- 알려진 KIND URL을 bounded first workflow로 사용한다. HTML 목록은 공식 문서화 API가 아니다.
- 배치 자동화와 공급원 계약은 별도 범위로 구분한다.

## 8. 범위 제한

- 기존 past `ESTIMATED_DART` 행만 대상으로 한다.
- IPO 행을 새로 합성하지 않는다.
- 기존 명시적 수동 편집은 사용자 작업으로 우선한다.
- 자동 갱신은 수동 값과 `CONFIRMED_KRX` 값을 보존한다.
- KRX 확정 시 DART 원래 날짜/receipt를 보존하고, KRX 증거 참조 하나만 최소로 추가한다.
- 운영 DB 변경, 배포, push, merge는 이 단계에서 하지 않는다.
