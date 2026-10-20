# 상장일 시초가로 매도단가 자동 채움 — 세부 기능 분해 (listing-open-price breakdown)

> **Epic**: Epic 21 (상장일 시초가로 매도단가 자동 채움, listing-open-price)
> **분야**: 백엔드(SF-1, SF-2 일부) + 프론트엔드(SF-2 일부, SF-3)
> **작성일**: 2026-10-16 (planning 워커)
> **근거 문서**: `docs/research/listing-open-price.md`(2026-10-16), `docs/spec/listing-open-price.md`, `docs/architecture/listing-open-price.md`
> **의존 관계**: Epic 5(IPO 기준정보)·Epic 17(일정 동기화)이 이미 병합된 `ipos` 테이블 위에 얹는다. 별도 선행 Epic 없음.

---

## 이 Epic의 특이사항 (분해 전에 반드시 인지)

1. **시세 출처는 이미 확정된 사용자 결정이다(2026-10-16).** 공공데이터포털 "금융위원회_주식시세정보"(`getStockPriceInfo`), 키는 기존 `DATA_GO_KR_SERVICE_KEY`. 대체 소스를 새로 조사하지 않는다. **응답 item 필드(`mkp` 등 15종, 전부 JSON 문자열)와 V2 URL은 2026-10-16 Master 실호출로 확정됐다** — 구 V1 경로는 403이므로 쓰지 않는다.
2. **키 승인 완료(2026-10-16).** V2 경로 실호출 200으로 확인됐고, 구 V1 경로는 이 키로 403(`SERVICE_KEY_IS_NOT_REGISTERED_ERROR`)이라 쓰지 않는다. 장래에 키 만료·일한도 초과·403이 발생하면 배치가 실패 로그만 남기고 서버·매도 폼은 공모가 기본값으로 정상 동작한다(AC-2102).
3. **기존 기능을 바꾸지 않는다.** 증권사 API 연동(/broker-sync), 캡처 OCR 가져오기(#452, `CaptureImport`/`useCaptureImport`)는 그대로다. 규칙 "캡처/증권사 값 > 시초가 기본값 > 공모가 기본값"만 지킨다. 캡처 우선은 이미 선언 순서·isDirty로 구현돼 있어 **SF-3는 매도단가 값 선택과 안내 문구만 고친다.**
4. **멱등·재시도 구조로 단순성을 지킨다.** D일 시세는 D+1 영업일 13시 이후 반영되므로, 배치는 "시초가 NULL인 대상"을 매일 다시 본다. 성공한 종목은 건드리지 않는다. 별도 상태 테이블·마이크로배치를 만들지 않는다.
5. **수동 등록/수정 경로를 만들지 않는다.** 사용자가 시초가를 직접 치는 화면은 없다. 매도 수정 화면(`SaleEditDialog`)도 바꾸지 않는다.

---

## 착수 전 Master 확인 필요 (구현 게이트) — **전부 통과/결정됨(2026-10-16)**

```
[게이트(a)] 시세 API 키 승인 + 실호출 1회 (Master) — 통과(2026-10-16)
    실측 결과:
      1. V2 경로 https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2
         → 200 OK, header {resultCode:"00", resultMsg:"NORMAL SERVICE."}.
         구 V1 경로(.../service/GetStockSecuritiesInfoService/getStockPriceInfo)는 같은 키로
         403 SERVICE_KEY_IS_NOT_REGISTERED_ERROR — V1은 쓰지 않는다.
      2. item 필드(실측): basDt, srtnCd, isinCd, itmsNm, mrktCtg, clpr, vs, fltRt, mkp,
         hipr, lopr, trqu, trPrc, lstgStCnt, mrktTotAmt. 모든 값이 JSON 문자열
         (예: "mkp": "27400") — 숫자 파싱 필수.
      3. 파라미터: likeSrtnCd=<6자리>, basDt=YYYYMMDD(또는 beginBasDt/endBasDt),
         resultType=json, numOfRows 동작 확인. 상장 첫날 데이터 존재 확인
         (2026-10-15 상장 KOSDAQ 종목의 mkp=27400이 다음 날 조회됨).
         휴장일 기준일은 items 빈 0건.
    → SF-1 착수 가능. research/spec/architecture의 "미확인" 표기는 실측으로 교체 완료.

[게이트(b)] 종가(clpr) 저장 여부 — 결정됨(2026-10-16)
    → 시가(listing_open_price)만 저장한다. 종가는 저장하지 않는다(필요해지면 그때 추가).
```

---

## 세부 기능(SF) 개요

총 **3개 SF**로 분해한다. "백엔드 수집 → 응답 노출 → 폼 기본값" 순서로, 각각 독립 검증 가능하다.

```
SF-1 (백엔드: V29 마이그레이션 + 시세 클라이언트 + 일일 배치)
  → SF-2 (IPO 응답에 listingOpenPrice 노출 + 프론트 Zod 스키마)
       → SF-3 (매도 폼 시초가 기본값 + 안내 문구 + 테스트)
```

| SF | 내용 | 선행 SF | 예상 워커 |
|---|---|---|---|
| SF-1 | 마이그레이션·클라이언트·배치(backend) | 없음 (게이트(a)(b) 통과·결정 완료) | backend |
| SF-2 | 응답 DTO·Zod 스키마(backend+ui 소) | SF-1 | backend |
| SF-3 | 매도 폼 기본값·문구·테스트(ui) | SF-2 | frontend |

---

## SF-1: 백엔드 — V29 마이그레이션 + 시세 클라이언트 + 일일 배치

**범위**:
- `ipos.listing_open_price`(BIGINT NULL) 컬럼 추가 마이그레이션 V29(+COMMENT). 종가 컬럼은 넣지 않는다(U-2 결정, 2026-10-16).
- `getStockPriceInfo` 호출 클라이언트(기존 `DataGoKrApiProperties`·`HttpClientSupport` 재사용, `RequestThrottle` 200ms).
- 일일 배치 스케줄러(14:30 KST, `@ConditionalOnProperty(app.open-price-sync.enabled)`)와 동기화 서비스(대상 선별·호출·멱등 저장).
- `Ipo` 엔티티에 `listingOpenPrice` 필드 추가.
- `application.yml` 설정 키, `application-test.yml` off, `build.gradle.kts` `tasks.test` systemProperty 한 줄.

**산출물 파일 경로**:
- `backend/src/main/resources/db/migration/V29__add_ipo_listing_open_price.sql`
- `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/openprice/StockPriceClient.java`
- `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/openprice/OpenPriceApiException.java`
- `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/openprice/IpoOpenPriceSyncService.java`
- `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/openprice/IpoOpenPriceSyncScheduler.java`
- `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java` (필드 추가)
- `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `backend/build.gradle.kts` (스위치 추가)
- 테스트: `StockPriceClientTest`(MockRestServiceServer), `IpoOpenPriceSyncServiceTest` 등

**특이사항**:
- 패키지·클래스명은 architecture §3 가칭을 따르되 Master 확정.
- 엔드포인트 상수 1곳 — **V2에 고정(2026-10-16 실측, V1은 403이라 쓰지 않는다).**
- 파싱 봉투는 `KasiSpecialDayClient` 패턴(`resultCode "00"`, items.item 3형태) 재사용.
- 응답은 `srtnCd` 일치 행만 채택, `mkp > 0`만 저장(0·빈 값은 NULL). **모든 값이 JSON 문자열이므로 Long 파싱 필수**(`"mkp": "27400"`).
- 인증키 미설정→`IllegalStateException`→WARN 중단, 403/인증 코드→ERROR 중단(재시도 없음), 앱 안 재시도 없음 — 해당 종목 건너뛰고 다음 날 배치가 재조회(멱등).
- 키가 든 URI·예외 메시지·로그 금지(기존 KASI 원칙).
- **실제 네트워크 호출 금지**(픽스처 JSON은 2026-10-16 실측 기준으로 작성 — 아래 테스트 방법).

**AC 초안**:
- AC-2101: 배치가 상장일이 지난 공모주의 시초가를 수집해 `listing_open_price`에 저장한다
- AC-2102: 키 미설정·403 시 배치만 중단되고 서버·다른 기능은 정상이다
- AC-2103: `stock_code` NULL 공모주는 조회 시도 없이 건너뛴다
- AC-2109: `mkp=0`/빈 값은 유효값으로 쓰지 않고 NULL로 둔다
- AC-2110: 테스트 실행 시 배치가 비활성화된다(`app.open-price-sync.enabled=false`)
- 추가: 멱등(이미 저장된 값 유지), D+1 반영 전 실행 시 0건 처리 후 다음 날 재시도, likeSrtnCd 오매칭 방지 필터

**테스트 방법**: `DB_PASSWORD=ipo_local_password ./gradlew test --rerun`. 클라이언트는 `MockRestServiceServer` 픽스처. **픽스처 명세(2026-10-16 실측 기준, 값은 전부 문자열 — 숫자처럼 보여도 `"27400"` 형태)**:

1. 정상 1건: `response.header{resultCode:"00",resultMsg:"NORMAL SERVICE."}` + `response.body.items.item=[{"basDt":"20261015","srtnCd":"123456","itmsNm":"예시","mkp":"27400","clpr":"17470",...}]`
2. item 단일 객체(배열 아님) 형태 — `ACCEPT_SINGLE_VALUE_AS_ARRAY` 처리
3. `items=""`(0건, 휴장일)
4. 에러 30: `resultCode="30"`(`SERVICE_KEY_IS_NOT_REGISTERED_ERROR`)
5. `mkp="0"`(0은 유효값 아님 → NULL, AC-2109)

통합은 기존 테스트 DB 패턴.

---

## SF-2: IPO 응답에 시초가 노출 — IpoResponse + 프론트 Zod

**범위**:
- `IpoResponse`에 `listingOpenPrice` 필드 추가 + `from()` 매핑(`fixedPrice` 옆).
- 종가(`listingClosingPrice`)는 노출하지 않는다(U-2 결정: 시가만 저장).
- 프론트 `ipoSchema`에 `listingOpenPrice: z.number().nullish()` 추가(`refundDate` 패턴).
- `IpoRegisterRequest`/`IpoUpdateRequest`는 변경 없음(수동 입력 경로 없음).

**산출물 파일 경로**:
- `backend/src/main/java/com/ipocalc/ipocatalog/controller/ipo/dto/IpoResponse.java`
- `ui/src/entities/ipo/types.ts`
- 테스트: `IpoResponse` 직렬화 테스트, `ipoSchema` 파싱 테스트(기존 파일 확장)

**특이사항**:
- `GET /ipos`·`GET /ipos/{id}` 두 경로 모두 같은 DTO를 쓰므로 변경 한 번으로 노출 완료.
- 구버전 클라이언트 호환: 필드가 없는 응답도 Zod `.nullish()`로 파싱 생존.
- 대시보드·달력 등 다른 IPO 소비자는 신규 nullable 필드의 영향 없음(추가 필드만 늘어남).

**AC 초안**:
- AC-2104: `GET /ipos/{id}`·`GET /ipos` 응답에 `listingOpenPrice`가 노출된다(값 있으면 숫자, 없으면 null, Zod 파싱 성공)

**테스트 방법**: 기존 DTO/Zod 테스트 확장 + `./gradlew test`, 프론트 vitest.

---

## SF-3: 매도 폼 — 시초가 기본값 + 안내 문구

**범위**:
- `useSaleForm.ts`: 매도단가 기본값을 `ipo?.listingOpenPrice ?? resolveIpoPrice(...)`로 변경. isDirty·캡처 우선 순서는 그대로 둔다(구조 변경 금지, 값 선택만).
- `SaleBasicFields.tsx`: 매도단가 안내 문구 분기 — 시초가가 채워졌으면 시초가 안내, 아니면 기존 공모가/무소식 문구(문안 후보는 spec U-6, 구현 중 Master 검토).
- 테스트: 시초가 있음(시초가 채움)/없음(공모가 채움)/0(공모가)/isDirty 유지/캡처 우선.

**산출물 파일 경로**:
- `ui/src/features/sale-register/SaleRegisterForm/useSaleForm.ts`
- `ui/src/features/sale-register/SaleRegisterForm/SaleBasicFields.tsx`
- 테스트: 기존 `useSaleForm` 테스트 파일 확장(패키지 내 테스트 규칙 준수)

**특이사항**:
- **건드리지 않는 것**: `useCaptureImport.ts`, `CaptureImport.tsx`, `SaleEditDialog.tsx`, 증권사 연동(/broker-sync), 수량·매도일 칸, `syncLinked` 계산.
- 캡처 우선은 `pendingCapture` effect가 기본값 effect 뒤에 선언돼 있는 기존 구조로 유지 — 재구현하지 않는다.
- 서버 계산(취득 단가 `acquisitionUnitPrice`)은 바꾸지 않는다. 시초가가 기본값으로 쓰일 뿐, 저장 후 수익 계산의 기준 단가는 기존 규칙(배정단가 우선)이다 — **혼동 금지**.
- 사용자 가이드(`docs/user-guides/manual/`) 갱신은 SF-3 병합 흐름에서 guide-planning §4 규칙에 따른다(문구는 소스에서 그대로).

**AC 초안**:
- AC-2105: 시초가가 있으면 매도단가에 시초가가 들어간다
- AC-2106: 시초가가 없으면 기존 공모가 규칙이 그대로 적용된다
- AC-2107: 사용자가 이미 고친 매도단가는 덮어써지지 않는다
- AC-2108: 캡처 체결가가 시초가 기본값보다 우선한다

**테스트 방법**: 기존 ui 테스트 패턴(vitest + RTL) 확장. 브라우저 QA: 매도 등록 화면에서 시초가/공모가 두 경로 육안 확인(수정 화면 무변경 포함).

---

## 참고

- **문서 의존성**: `docs/research/listing-open-price.md`(전체), `docs/spec/listing-open-price.md`(AC·엣지 케이스 본체), `docs/architecture/listing-open-price.md`(DB·배치·설정)
- **기존 코드 재사용 지도**: `KasiSpecialDayClient`(파싱·키 검증), `DataGoKrApiProperties`(키·base-url), `HttpClientSupport`(타임아웃·스로틀), `IpoSyncScheduler`(enabled 스위치), `build.gradle.kts tasks.test`(테스트 off)
- **검증 명령**: `DB_PASSWORD=ipo_local_password ./gradlew test --rerun && ./gradlew build`, 프론트 기존 vitest/lint
- **금지사항**: 캡처·증권사 연동 코드 수정, `SaleEditDialog` 수정, 시초가 수동 입력 화면 추가, 키 로그 노출, 실측과 다른 DTO 필드(문자열→Long 파싱 누락 포함) 임의 추가, 커밋(이 세션은 문서만)

---

## 미결정 사항

| # | 항목 | 설명 | 결정 | 상태 |
|---|---|---|---|---|
| U-1 | 응답 필드·V1/V2 URL 실측 | 게이트(a) 실호출 완료 | **V2 고정(V1 403 폐기), item 15종·문자열 확정(2026-10-16)** | **결정됨** |
| U-2 | 종가(`listing_closing_price`) 저장 | 게이트(b) 결정 완료 | **저장하지 않음 — 시가만(2026-10-16)** | **결정됨** |
| U-3 | 관리자 수동 동기화 | 제외 결정 | **만들지 않는다 — 배치만(2026-10-16)** | **결정됨** |
| U-4 | 라이선스 4유형 인지 | 공공누리 4유형(상업적 이용금지·재배포 금지) | **비영리로 사용하므로 4유형 조건에서 그대로 사용(2026-10-16)** | **결정됨** |
| U-5 | 배치 시각 14:30 KST | D+1 13:00 반영에 맞춘 제안 | **14:30 KST 유지(2026-10-16)** | **결정됨** |
| U-6 | 안내 문구 문안 | "상장일 시초가 X원이 먼저 들어갑니다." 등 후보 | Master(SF-3 구현 중) | SF-3 |
| U-7 | "상장일 시가로 전량 매도" 원클릭 | 이번 범위 밖 확정. 후속 후보로만 보존 | 없음(기록만) | 후속 로드맵 |
| U-8 | 영리화 시 시세 출처 전환 | 영리 전환 시 사용자 본인 증권사 API 키 조회로 전환(우리가 시세를 모아 재배포하지 않는 구조). 약관 원문 확인 필요. 참고: research·architecture의 "영리화 시 대안" 절 | 없음(기록만) | 후속 로드맵 |
