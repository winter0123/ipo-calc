# 상장일 시초가 자동 수집 조사 문서 (listing-open-price)

> **Epic**: Epic 21 (상장일 시초가로 매도단가 자동 채움, listing-open-price)
> **작성일**: 2026-10-16 (planning 워커)
> **조사 방식**: 공공데이터포털 공식 페이지 WebFetch(한·영문), 기존 코드 분석, 2차 출처 병행 표기
> **사용자 결정 반영(2026-10-16, 재조사 대상 아님)**: 매도 등록 화면의 첫 매도단가 기본값은 "그 공모주의 상장일 시초가". 시초가를 모르면 지금처럼 공모가. 사용자는 값 고침 가능. 시세 출처는 공공데이터포털 "금융위원회_주식시세정보"(`GetStockSecuritiesInfoService` / `getStockPriceInfo`). 키는 기존 `DATA_GO_KR_SERVICE_KEY` 사용. 증권사 API 연동·캡처 OCR 가져오기(#452)는 그대로 유지하며 캡처/증권사 값이 시초가 기본값보다 우선. "상장일 시가로 전량 매도 기록" 원클릭 버튼은 이번 범위 밖(미결정/후속 후보).

## 조사 목적

Epic 21 구현을 위한 사전 조사. 매도 등록 폼의 매도단가(판매가) 기본값을 "공모가 → 상장일 시초가"로 바꾸기 위해, 시초가를 자동 수집할 외부 API(금융위원회_주식시세정보)의 요청·응답 스펙, 데이터 반영 시점, 호출 한도, 라이선스, 기존 코드(공공데이터포털 클라이언트·일일 배치) 재사용 가능성을 확인한다.

## 조사 대상

- getStockPriceInfo의 요청 파라미터와 응답 필드(공식 문서 기준)
- 데이터 반영 시점(상장 당일 데이터 확인 가능 시점), 호출 한도, 에러 코드
- 상장 첫날 데이터 존재 여부, 종목코드 없는 공모주 처리
- 라이선스·상업적 이용 조건
- 기존 `DATA_GO_KR_SERVICE_KEY`·클라이언트·배치 패턴 재사용

## 출처

- **공식(주)**: https://www.data.go.kr/data/15094808/openapi.do (금융위원회_주식시세정보, WebFetch 2026-10-16)
- **공식(영문, 요청 파라미터 표 포함)**: https://www.data.go.kr/en/data/15094808/openapi.do (WebFetch 2026-10-16)
- **공식 메타데이터 JSON**: https://www.data.go.kr/catalog/15094808/openapi.json (WebFetch 2026-10-16)
- **참고 문서(파일 다운로드, 본 워커는 열람하지 못함)**: "금융위원회_주식시세정보_활용자가이드.docx" (공식 페이지 참고문서 탭에 존재)
- **2차 출처(응답 item 필드 확인용, 공식 아님)**: https://github.com/joshua-jingu-lee/ante/blob/main/docs/references/external-apis/data-go-kr-stock-price-api.md , https://wannagobacktodgte.tistory.com/17 , https://www.musi.co.kr/archives/95711 (XML 샘플·필드명 일치 확인)
- **기존 코드**: `KasiSpecialDayClient.java`, `KasiClientConfig.java`, `DataGoKrApiProperties.java`, `HttpClientSupport.java`, `IpoSyncScheduler.java`, `IpoDetailSyncScheduler.java`, `MarketHolidaySyncScheduler.java`, `build.gradle.kts`, `application.yml`

## 확인한 내용

### 1. API 개요 (공식 페이지, 2026-10-16 WebFetch)

| 항목 | 값 | 근거 |
|---|---|---|
| OpenAPI 명 | 금융위원회_주식시세정보 | 공식 페이지 기본정보 |
| 제공기관 | 금융위원회 (원천: 한국거래소 시세 정보 연계) | 공식 페이지 |
| 분류 | 일반공공행정 - 재정·금융 | 공식 페이지 |
| 등록일/수정일 | 2021-11-16 / 2026-09-07 | 공식 페이지 |
| API 유형 / 포맷 | REST / JSON+XML | 공식 페이지 |
| 구성 | 오퍼레이션 4개: ① 주식 시세(getStockPriceInfo) ② 수익증권 시세 ③ 신주인수권증권 시세 ④ 신주인수권증서 시세 | 공식 페이지 설명 |
| 본 Epic 사용 대상 | ① 주식 시세 — "상장 주식의 시세 정보를 종목 코드 및 일자 기준으로 조회" | 공식 페이지 설명 |
| 비용 | 무료 | 공식 페이지 이용 조건 |
| 승인 | 개발단계 자동승인 / 운영단계 자동승인 | 공식 페이지 |
| 신청 가능 트래픽 | **개발계정 일 10,000회** / 운영계정은 활용사례 등록 후 증가 신청 가능 | 공식 페이지 |
| 서비스 URL (공식 영문 페이지 Request Link) | `https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2` (Service URL: `https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2`) — **Master 실호출로 정답 확정(2026-10-16, 200 OK)** | 공식 영문 페이지 + Master 실측 |
| 구 URL(V1, 2차 출처) | `https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo` — 같은 키로 **403 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`**. **V1은 쓰지 않는다**(2026-10-16 Master 실측) | Master 실측 + 2차 출처 |

### 2. 요청 파라미터 (공식 영문 페이지 Request Parameter 표, 2026-10-16 확인)

| 파라미터 | 필수/선택 | 설명 (공식 표 기준) | 본 Epic 사용 계획 |
|---|---|---|---|
| `serviceKey` | **필수** | 공공데이터포털 인증키 | 기존 `DATA_GO_KR_SERVICE_KEY` (§7 참고) |
| `numOfRows` | 선택 | 한 페이지 결과 수 | 10 등 소수 (종목당 1건 조회) |
| `pageNo` | 선택 | 페이지 번호 | 1 |
| `resultType` | 선택 | xml/json 구분, 기본 xml | `json` (KASI 클라이언트와 동일 파싱 방식) |
| `basDt` | 선택 | 기준일자 **일치** 검색 | **사용**: `basDt=<상장일 YYYYMMDD>` |
| `beginBasDt` | 선택 | 기준일자 이상(>=) | 미사용(예비) |
| `endBasDt` | 선택 | 기준일자 **미만(<)** 주의 | 미사용(예비) |
| `likeBasDt` | 선택 | 기준일자 포함 검색 | 미사용 |
| `likeSrtnCd` | 선택 | 단축코드 **포함** 검색 | **사용**: `likeSrtnCd=<ipos.stock_code 6자리>`. 주의: 공식 표에 단축코드 **일치** 검색 파라미터(`srtnCd`)는 없다 — 응답받은 `srtnCd`가 입력 코드와 같은지 필터링 필요(§5) |
| `isinCd` / `likeIsinCd` | 선택 | ISIN 일치/포함 | 미사용 |
| `itmsNm` / `likeItmsNm` | 선택 | 종목명 일치/포함 | 미사용(종목코드가 정확한 키) |
| `mrktCls` | 선택 | 시장구분 일치 검색 | 미사용(허용값은 공식 표에 미명시 — **미확인**) |
| `beginVs`/`endVs`, `beginFltRt`/`endFltRt`, `beginTrqu`/`endTrqu`, `beginTrPrc`/`endTrPrc`, `beginLstgStCnt`/`endLstgStCnt`, `beginMrktTotAmt`/`endMrktTotAmt` | 선택 | 등락·거래량·시총 등 범위 검색 | 미사용 |

> 요청 URL 형태(게이트웨이 경유형 공공데이터포털 표준): `GET <Service URL>/<오픈API경로>?serviceKey=...&resultType=json&basDt=...&likeSrtnCd=...`. 인증키는 URL 쿼리에 실리므로 로그·예외 메시지에 남기지 않는다(KASI 클라이언트와 동일 원칙, `KasiSpecialDayClient.java:102-105` 참고).

### 3. 응답 필드 (2026-10-16 Master 실호출로 확정)

**공식 웹 표(영문 페이지 Response Element)에는 `header`, `body` 두 항목만 표시되어 있지만, Master 실호출(V2 경로, `resultType=json`)로 `body.items.item`의 실제 필드를 확정했다. `header.resultCode="00"`, `resultMsg="NORMAL SERVICE."`로 정상 응답.**

**중요(실측): item의 모든 값은 JSON 문자열이다(예: `"mkp": "27400"`). 숫자로 파싱해야 한다.**

| 필드(실측) | 의미 | 본 Epic 사용 계획 | 확인 상태 |
|---|---|---|---|
| `basDt` | 기준일자 (YYYYMMDD, 문자열) | 요청 `basDt`와 일치 검증 | 실측(2026-10-16) |
| `srtnCd` | 단축코드 (6자리 종목코드, 문자열) | `ipos.stock_code`와 일치 필터링 | 실측(2026-10-16) |
| `isinCd` | ISIN 국제고유번호 | 미사용 | 실측(2026-10-16) |
| `itmsNm` | 종목명 | 로그·검증용(선택) | 실측(2026-10-16) |
| `mrktCtg` | 시장구분(실측 사례: `KOSDAQ`) | 미사용 | 실측(2026-10-16) |
| **`mkp`** | **시가(정규시장 최초가격, 문자열 — 예: `"27400"`)** | **`ipos.listing_open_price` 매핑 대상. Long으로 파싱** | 실측(2026-10-16) |
| `clpr` | 종가(정규시장 최종가격, 문자열 — 예: `"17470"`) | **저장하지 않는다(U-2 결정, 2026-10-16). 필요해지면 그때 추가** | 실측(2026-10-16) |
| `hipr` / `lopr` | 고가 / 저가 | 미사용 | 실측(2026-10-16) |
| `vs` / `fltRt` | 전일 대비 / 등락률 | 미사용 | 실측(2026-10-16) |
| `trqu` / `trPrc` | 거래량 / 거래대금 | 미사용 | 실측(2026-10-16) |
| `lstgStCnt` / `mrktTotAmt` | 상장주식수 / 시가총액 | 미사용 | 실측(2026-10-16) |

응답 봉투 구조(실측): `response.header{resultCode,resultMsg}` + `response.body{numOfRows,pageNo,totalCount,items.item[...]}` — KASI 특일 정보(`KasiSpecialDayClient`)와 동일한 공공데이터포털 표준 구조였다. `resultCode` 정상값 `"00"`도 실측 확인.

### 4. 데이터 반영 시점 (공식 페이지 명시)

- **데이터 갱신주기: 일 1회.**
- **"금융위원회의 모든 API는 실시간이 아니며, 데이터 갱신은 기준일자로부터 영업일 하루 뒤 오후 1시 이후에 업데이트된다. 예: 금요일 데이터는 차주 월요일 제공(월요일이 공휴일이면 다음 영업일)."** (공식 페이지 설명, 2026-10-16 확인)
- **해석(본 Epic에 미치는 영향)**: 상장일(=시초가 기준일, D)의 시세는 **D+1 영업일 13:00 이후**에 API에서 확인 가능한다. 즉 상장 당일 배치로는 시초가를 얻을 수 없다. 일 1회 배치가 D+1 오후 이후에 실행되거나, D+1 이후 날짜에 대상 종목을 다시 조회(재시도)해야 한다(architecture §5 설계 반영).
- **실측 정합(2026-10-16 Master)**: 오후 5시경 호출 시 기준일자 10-15까지의 행은 조회되고(10-15 상장 종목의 `basDt=20261015` 행 포함), 10-16 당일 행은 아직 없었다 — 공식 설명(D+1 반영)과 일치.

### 5. 상장 첫날 데이터 존재 여부 (2026-10-16 실측 — 존재 확인)

- **존재한다(Master 실측).** 2026-10-15 신규 상장 KOSDAQ 종목의 `basDt=20261015` 행에 `mkp=27400`, `clpr=17470`이 **상장 다음 날(10-16 오후 5시경) 조회됐다.** 즉 상장 첫 거래일의 시가 행이 제공된다.
- 파라미터도 실측 확인: `likeSrtnCd=<6자리 종목코드>`, `basDt=YYYYMMDD`(또는 `beginBasDt`/`endBasDt`), `resultType=json`, `numOfRows`가 모두 동작한다.
- 휴장일 기준일(예: 2026-10-05) 조회는 `items`가 비어 0건으로 온다 — 에러가 아니고, 배치는 0건을 "값 없음"으로 처리하면 된다.
- 대응(설계, 유지): 0건이면 `listing_open_price`는 NULL 유지 → 매도 폼은 기존대로 공모가 기본값. 수집 실패가 서비스 장애가 되지 않는다.

### 6. 호출 한도와 에러 코드 (공식 페이지)

| 구분 | 값 | 근거 |
|---|---|---|
| 일일 한도 | 개발계정 10,000회 / 운영계정 증가 신청 필요 | 공식 페이지 트래픽 표기 |
| 초당 한도 | **미확인**(공식 표에 수치 없음. 에러코드 23이 존재하므로 초당 제한은 있음) | 공식 페이지 에러코드 |
| 일일 초과 시 | `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` (22) | 공식 페이지 에러코드 표 |
| 초당 초과 시 | `LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR` (23) | 공식 페이지 에러코드 표 |
| 키 미포함 | `SERVICE_KEY_IS_NULL` (20) | 공식 페이지 |
| 권한 없음(활용신청 미완료 등) | `PERMISSION_DENIED` (20), `SERVICE_ACCESS_DENIED_ERROR` (20) | 공식 페이지 |
| **키 미등록(활용신청 미서비스)** | **`SERVICE_KEY_IS_NOT_REGISTERED_ERROR` (30)** — "등록되지 않은 API 인증키. 활용신청이 정상적으로 완료되었는지 확인". **실측: V1 경로 호출 시 이 코드가 실측 확인됨(2026-10-16). V2 경로는 승인 완료로 200** | 공식 페이지 + Master 실측 |
| 키 만료 | `DEADLINE_HAS_EXPIRED_ERROR` (31) | 공식 페이지 |
| 파라미터 오류 | `INVALID_REQUEST_PARAMETER_ERROR` (10) | 공식 페이지 |
| 서비스 미존재/폐기 | `NO_OPENAPI_SERVICE_ERROR` (12) | 공식 페이지 |
| 일시 장애 | `APPLICATION_ERROR` (01), `HTTP_ERROR` (04), `SERICETIMEOUT_ERROR` (05 — 표기: SERVICETIMEOUT_ERROR), `BLACKLIST_IP_ACCESS_ERROR` (29) | 공식 페이지 |

- 호출량 산정: 대상 종목당 1호출(상장일 기준). 하루 신규 상장 종목 수는 통상 수~수십 건 수준이며, 미동기화 누적분이라도 10,000/일 개발 한도 내로 충분하다. 그래도 `HttpClientSupport.RequestThrottle`로 호출 간격(예: 200ms)을 둔다(KASI 클라이언트와 동일한 자체 안전장치).

### 7. 인증키 현황 (사용자 확정 사실, 2026-10-16)

- 키는 기존 환경변수 `DATA_GO_KR_SERVICE_KEY`(`application.yml`의 `app.datagokr.service-key: ${DATA_GO_KR_SERVICE_KEY:}`)를 그대로 쓴다. 새 환경변수를 만들지 않는다.
- 이 키는 본 Epic 작성 당시 주식시세정보 서비스에 활용신청이 안 돼 있어 403 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`였다(사용자 신청 중). **2026-10-16에 활용신청이 승인되어 V2 경로 실호출이 200 OK로 확인됐다**(아래 실호출 검증 참고. V1 경로는 여전히 403 — 경로 문제이지 키 문제가 아니다).
- 참고: 같은 키로 한국천문연구원 특일 정보(`B090041/getRestDeInfo`)도 정상 동작한다(휴장일 배치). 공공데이터포털은 **서비스별 활용신청**임을 기억한다(§6 에러 30).
- **실호출 검증 상태: 완료(2026-10-16, Master).** V2 경로 200 + `resultCode="00"`, item 필드 15종(§3)과 문자열 타입, 상장 첫날 데이터(§5), D+1 반영(§4), 휴장일 0건을 모두 확인했다. 남은 미확인은 §불확실한 점 참조.

### 8. 라이선스 (공식 페이지, 주의 필요)

- **공공누리 제4유형: 출처표시 + 상업적 이용금지 + 변경금지.**
- "제3자 무단 제공 및 재배포가 엄격히 금지. 상업적 목적으로 이용할 경우 원천 소유자인 한국거래소 KRX Data Marketplace(data.krx.co.kr)에서 유료 구매 필요." (공식 페이지 명시)
- 본 프로젝트(개인용 투자 기록 도구)가 비영리·비상업 전제라면 이용 가능하나, **상업화·서비스 전환 시에는 이 데이터 사용을 전환해야 한다.** 결정됨(2026-10-16): 비영리로 계속 사용, 영리화 시에는 아래 "영리화 시 대안" 절 참조.

### 9. 기존 코드 재사용 가능 항목

| 기존 코드 | 재사용 방식 | 근거 |
|---|---|---|
| `DataGoKrApiProperties` (`app.datagokr.service-key`, `base-url: https://apis.data.go.kr`) | **그대로 재사용**. 새 키 환경변수 불필요 | `application.yml:58-60` |
| `HttpClientSupport.requestFactory()` (연결 5초·읽기 15초) | 신규 클라이언트도 동일 사용 | `HttpClientSupport.java:19-22` |
| `HttpClientSupport.RequestThrottle` | 호출 간격 보장에 재사용 | `HttpClientSupport.java:25-48` |
| `KasiSpecialDayClient` 파싱 패턴(`resultCode "00"`, items.item 단일/배열/빈문자열 3형태) | 신규 `StockPriceClient`가 같은 공공데이터포털 봉투 구조를 파싱하는 데 참고 | `KasiSpecialDayClient.java:141-199` |
| `KasiClientConfig` (빈 정의, 키 없어도 기동 성공) | 신규 클라이언트 빈도 같은 방식 | `KasiClientConfig.java` |
| `IpoSyncScheduler` (`@ConditionalOnProperty(app.ipo-sync.enabled)`, 04:30 KST, 지수 백오프 5→10→20분) | 신규 배치 스케줄러의 스위치·재시도 패턴 모범 | `IpoSyncScheduler.java` |
| `IpoDetailSyncScheduler` (05:00 KST, 같은 enabled 키 공유) | 배치 시각 상대 위치 참고(새 배치는 이들 뒤) | `IpoDetailSyncScheduler.java` |
| `build.gradle.kts` `tasks.test { systemProperty("app.holiday-sync.enabled","false"); systemProperty("app.ipo-sync.enabled","false") }` | 새 배치 스위치도 동일하게 테스트에서 끈다 | `build.gradle.kts:55-63` |
| `ipos.stock_code` (V5, nullable — "상장 전이면 NULL일 수 있다") | 조회 키. NULL이면 수집 제외 | `V25` COMMENT 58행 |

### 10. 종목코드 없는 공모주 처리 (ipos.stock_code NULL)

- API 조회 키가 종목코드(`likeSrtnCd`)인데 `ipos.stock_code`가 NULL이면 **조회 불가** — 수집 대상에서 제외하고 NULL을 그대로 유지한다.
- 영향: 해당 공모주의 매도단가 기본값은 기존대로 공모가(사용자 결정의 "시초가를 모르면 공모가" 경로와 동일). 별도 안내 없이 자연스럽게 처리된다.
- 시점: 상장 전 종목은 원래 `stock_code`가 NULL이거나 상장 후 동기화로 채워진다(Epic 17/기존 정책). 본 배치는 `listing_open_price IS NULL AND stock_code IS NOT NULL AND listing_date <= 어제 AND status='ACTIVE'`인 행만 본다.

## 리스크 분석 및 완화 방안

| 리스크 | 구체적 상황 | 영향도 | 완화 방안 |
|---|---|---|---|
| 키 승인 | ~~승인 대기~~ → **2026-10-16 승인 완료(V2 경로 200 실측)**. 장래에 키 만료(31)·일한도(22)가 발생하면 기능 스위치로 배치만 중단, 서버·다른 기능 정상 유지(architecture §6). 그 외 장애 시 매도 폼은 공모가 기본값으로 동작 | 중→해소 | architecture §6 |
| 응답 스키마 | ~~불일치 우려~~ → **2차 출처 필드명(`mkp` 등)이 실제 응답과 일치함을 실측으로 확인(2026-10-16). 단, 모든 값이 JSON 문자열 — 숫자 파싱 주의** | 중→해소 | Master 실측 |
| V1/V2 URL 혼선 | ~~우려~~ → **V2가 정답으로 확정(200). V1은 같은 키로 403 — 쓰지 않는다. 엔드포인트 상수를 V2에 고정** | 중→해소 | Master 실측 |
| D+1 13:00 반영 지연 | 상장 당일 배치가 빈 응답 | 낮음 | 대상 조건을 `listing_date <= 어제`로 두고, 값이 NULL이면 다음 날 재시도(멱등) |
| likeSrtnCd 부분 일치 오매칭 | 다른 코드에 동일 숫자열 포함 | 낮음 | 응답 `srtnCd` 일치 필터링 |
| 라이선스 4유형 | 상업적 이용 금지 | (장래) | 비영리 전제 유지 확정(2026-10-16). 영리화 시 전환 — "영리화 시 대안" 절 |
| 시가 0·비정상값 | API가 `mkp=0` 반환 | 낮음 | 0은 유효값으로 쓰지 않음(AC-2109) |
| 기존 데이터 충돌 | 관리자가 이미 시초가를 수동 입력한 경우 | 낮음 | 본 Epic은 수동 입력 경로를 만들지 않는다(수동 입력 자체가 현재 없음). 배치 값이 기본값으로만 쓰이고 사용자 수정 우선 |

## 영리화 시 대안 (참고 — 현 시점은 비영리 전제)

영리로 전환하면 이 API(공공데이터포털 금융위원회_주식시세정보)를 그대로 쓸 수 없다(공공누리 4유형: 상업적 이용·재배포 금지).

- **채택 방향**: 사용자 본인의 증권사 API 키(이미 있는 한국투자·키움·LS 조회 전용 연동)로 그 사용자 화면에 필요한 상장일 시세만 조회한다 — 우리가 시세를 모아 재배포하지 않는 구조.
- 연동하지 않은 사용자: 공모가 기본값 + 캡처 OCR(#452)로 채운다(기존 폴백과 동일하게 유지).
- 다른 대안(참고, 유료): KRX 데이터 이용 계약, 코스콤·에프앤가이드 등 시세 벤더 계약.
- 각 증권사 API 약관은 영리화 시점에 원문 확인 필요 — 지금 확정하지 않는다.
- 설계 영향: 시세 조회는 `StockPriceClient` 한 곳에 모으므로 출처가 교체돼도 매도 폼·DB는 그대로 쓴다(architecture §3의 단일 경유점이 이 전환을 가능하게 한다).

## 불확실한 점

**2026-10-16 Master 실호출로 해소된 항목**: 응답 item 필드(15종·문자열 타입) ✓, V2 URL ✓, 상장 첫날 데이터 존재 ✓, `resultCode="00"` ✓, 0건(`items` 빈 형태) ✓, `likeSrtnCd`·`basDt`·`resultType=json`·`numOfRows` 동작 ✓.

아래는 여전히 미확인이며, 임의로 확정하지 않는다.

1. 초당 호출 제한 수치(에러 코드 23 존재로 제한은 있음 — 200ms 스로틀로 방어).
2. `mrktCls` 등 일부 파라미터의 허용값(미사용이므로 영향 없음).
3. 단축코드 일치 전용 파라미터(`srtnCd`)의 별도 존재 여부(공식 표에는 없음. `likeSrtnCd`+응답 필터로 충분).

## 결론

- 금융위원회_주식시세정보(`getStockPriceInfo`)는 무료·자동승인·개발계정 일 10,000회로 본 Epic의 시초가 수집 소스로 **채택 확정**이다. **2026-10-16 Master 실호출**: V2 경로 200, `resultCode="00"`, item 필드 15종(`mkp` 시가 포함, 전부 JSON 문자열), 상장 첫날 데이터 존재, D+1 반영을 모두 확인했다. **엔드포인트는 V2에 고정하고 V1은 쓰지 않는다.**
- D일 시세는 D+1 영업일 13시 이후 반영되므로(실측 정합), 배치는 "상장일이 어제까지이고 시초가가 NULL인 종목"을 매일 한 번 재시도하는 멱등 구조로 설계한다(architecture §5).
- 기존 `DATA_GO_KR_SERVICE_KEY`·`DataGoKrApiProperties`·`HttpClientSupport`·`@ConditionalOnProperty` 배치 패턴을 그대로 재사용해 새 인프라를 만들지 않는다.
- 장애·키 문제 상황에서는 배치만 꺼지고 서버는 정상이며, 매도 폼은 기존 공모가 기본값으로 동작한다(사용자 결정의 폴백 경로와 동일).

---

## 미결정 사항

| # | 항목 | 설명 | 결정 | 상태 |
|---|---|---|---|---|
| U-1 | 응답 스키마·URL 확정 | V2 경로 + item 필드 15종(문자열 타입) 실측 완료 | **V2 고정, V1 폐기(2026-10-16)** | **결정됨** |
| U-2 | 종가(`clpr`) 저장 여부 | 같은 API 응답에 종가가 오나 저장하지 않는다 | **시가(`listing_open_price`)만 저장. 필요해지면 그때 추가(2026-10-16)** | **결정됨** |
| U-3 | 관리자 수동 동기화 엔드포인트 | 배치만 둘지 관리자 버튼 둘지 | **관리자 수동 동기화는 만들지 않는다(2026-10-16)** | **결정됨** |
| U-4 | 라이선스 4유형 인지 | 공공누리 4유형(상업적 이용금지·재배포 금지) | **비영리로 사용하므로 4유형 조건에서 그대로 사용(2026-10-16)** | **결정됨** |
| U-5 | "상장일 시가로 전량 매도 기록" 원클릭 | 사용자가 범위 밖으로 명시함 | 후속 Epic 후보로만 보존(이번 구현 안 함) | 미결정(범위 밖 확정) |
