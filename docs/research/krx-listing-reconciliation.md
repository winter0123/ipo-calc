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


## 9. 2026-11-24 자동 탐색 공급 검증 현황 (기존 PR #515)

- 공식 상세검색: `https://kind.krx.co.kr/disclosure/details.do` (종목코드 `repIsuSrtCd=A+6자리`, 게시일 `fromDate/toDate`, 페이지 `pageIndex`); 원문 뷰어 `https://kind.krx.co.kr/common/disclsviewer.do`, 공식 원문 `/external/YYYY/MM/DD/.../70760.htm`(신규) 및 `70791.htm`(추가).
- 검색은 100건/페이지, 최대 50페이지, 요청 구간 최대 1개월; 자동 실행은 최대 28일 구간으로 분할하며 접수번호 중복 제거. 페이지 반복·누락·HTML 구조 변경·HTTP 403은 `FETCH_FAILED`, 임의 공고 없음 판정 금지.
- 현재 브랜치의 수동 KIND live proof 기록: 추가상장 4건의 종목코드/게시일 검색→뷰어→원문 파싱은 [#45112576419](https://github.com/winter0123/ipo-calc/actions/runs/45112576419)에서 성공. 신규상장 3건은 같은 실행에서 **알려진 URL을 통한 원문 파싱만** 검증; 후속 [`6caa629d`](https://github.com/winter0123/ipo-calc/commit/6caa629d15f53d56f214f577998a38c26284a6c3)부터 검색→뷰어 경로 테스트가 추가됐으나 최신 SHA의 수동 라이브 검사는 별도 검증이 필요하다.
- 검색 제목의 신규/추가/정정·취소·철회·변경을 분류하고, 원공고 연결이 불확실한 정정 유형은 저장 금지. DART 원문과 KIND의 동일 발행회차를 독립 확인한 실제 추가상장 **0/4**이므로 이 4건을 자동 저장 성공으로 주장하지 않는다.
- HTML 검색은 공식 사이트의 구현 경로이지만 문서화된 안정적 OpenAPI/완전한 정정 이력 보장은 미확인. 운영에서 `auto-apply-enabled=false` 유지와 운영 공급 완결성 확인이 필수다.

## 자동 동기화 근거·운영 게이트 (2026-11-24)

- 공식 출처: KIND https://kind.krx.co.kr 의 `/disclosure/details.do` 상세검색, `/common/disclsviewer.do` 뷰어, `/external/YYYY/MM/DD/NNNNNN/NNNNNNNNNNNNNN/(70760|70791).htm` 원문. 종목코드와 게시일 28일 이하 창으로 검색하고 페이지가 완결됐을 때만 공고 없음으로 판단한다.
- 과거 수동 라이브 실행 #45112576419는 추가상장 4건의 검색·뷰어·원문 및 신규상장 3건의 **알려진 URL 원문 파싱**을 확인했다. 신규상장 3건의 URL 없는 발견은 테스트 추가 후 별도 수동 라이브 검증이 필요하다. KIND의 HTTP 403은 실제 접근 거부로, 성공/공고 없음으로 간주하지 않는다.
- 기존 실공시 DART XML 네 건과 KIND 추가상장 원문의 발행회차·발행일을 독립적으로 연결할 근거는 미확보: 자동 확정이 아니라 REVIEW_REQUIRED. 확인되지 않은 과거 37건은 공고 없음이 아니다. 원자료 41건의 운영 DB 재조사는 이 PR에서 실시하지 않는다.
- 공식 검색 페이지/정정·취소의 완결성 및 외부 접근성은 운영 쓰기 활성화 전 별도 게이트다. 기본 `app.kind-listing.auto-apply-enabled=false` 유지.
