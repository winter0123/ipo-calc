# 증권사 조회 전용(read-only) API 연동 파일럿 — 세부 기능 분해 (broker-readonly-api-pilot breakdown)

> **Epic**: Epic 18 (증권사 조회 전용 연동, broker-readonly-api-pilot)
> **분야**: 백엔드 위주(SF-1~5), 프론트엔드(SF-6)
> **작성일**: 2026-09-29 (Master가 research 문서를 근거로 직접 작성)
> **근거 문서**: `docs/research/broker-readonly-api-pilot.md`(전체, 특히 4.3·4.4·4.8·6·9절), `docs/roadmap.md` 1.1절·6절(E18 카드)
> **의존 관계**: 없음. 단, `Sale`/`Cost`/`SecuritiesFirm`/`SecuritiesAccount`(Epic 3·4·8) 위에 얹는 구조.
> **원칙(절대 규칙)**: 이 Epic의 모든 브로커 연동은 **조회(GET)만 수행한다. 매수/매도/정정/취소 등 주문 실행 코드는 어떤 형태로도 작성하지 않는다.** (research 5절 제약사항 1)

---

## 구현 게이트 조사 결과 (2026-09-30) — 이 Epic은 이 절부터 읽는다

KIS 공식 GitHub(`koreainvestment/open-trading-api`) 샘플 코드·README 기준 조사 결과다. 포털 화면은 동적이라 API 명세 본문은 읽지 못했다.

| 게이트 | 결과 | 확정 내용 |
|---|---|---|
| 1. 수수료·세금 필드 | 부분 확인 | 체결내역 조회에는 건별 수수료·세금이 없다. 기간별매매손익현황 조회(`TTTC8715R`)에 `fee`·`tl_tax`가 있으나 **일자×종목 집계 단위**라 Sale 1건과 1:1이 아닐 수 있다. `prsm_tlex_smtl`(추정제비용합계)의 의미와 공모주 배정분 표시·`pdno` 형식은 실계좌 1회 조회로만 확인된다. **사용자 결정(2026-09-30): 매도·수수료·세금 자동 채움은 `TTTC8715R` 기준으로 하고, 같은 날짜·같은 종목의 분할 매도는 합산해 Sale 1건으로 매칭한다.** 실계좌 1회 조회(`prsm_tlex_smtl` 의미·공모주 배정분 표시·`pdno` 형식 확인)도 승인됐으며, 앱키 발급 후 SF-3 완료 뒤 Master가 1회만 수행한다. |
| 2. 조회 전용 권한 옵션 | **해소(사용자 결정)** | "조회 전용"은 읽기전용 키를 뜻하지 않고 **쓰기·주문 기능을 코드에 만들지 않는다**는 뜻이다(2026-09-30). 키 권한 확인은 필요 없다. 방어는 코드 수준(아래 SF-3)으로 하고, 키가 주문 가능한 권한일 수 있다는 한계를 사용자 고지 문구에 명시한다. |
| 3. 접근토큰 | 확인됨 | 유효기간 1일, 만료시각이 응답에 온다. 재발급은 1분에 1회로 제한되고 refresh 토큰은 없다. **"매 호출 재발급" 전략은 폐기하고 만료 시각까지 암호화 캐시한다.** |
| 4. 계좌번호 파라미터 | 확인됨 | 앞 8자리(`CANO`)와 뒤 2자리(`ACNT_PRDT_CD`, 일반 주식은 "01")를 쿼리 파라미터로 보낸다. |

**진행 방식**: SF-1·SF-2·SF-3은 착수 가능하다. SF-4는 게이트 1의 집계 단위 매핑 규칙(위 결정: 일자×종목 합산)이 확정됐고, 실계좌 1회 조회 결과가 나온 뒤 필드 매핑(`prsm_tlex_smtl`, `pdno`)을 확정한다.

---

## 이 Epic의 특이사항

1. **비용(Cost) 자동 채움은 1차 범위에서 제외한다.** research 4.4-4 근거: 매도 수수료를 `Sale.fee`/`Sale.tax`와 `Cost`에 이중 기록하면 수익 계산이 이중 차감된다. **이 Epic은 `Sale` 자동 채움만 다룬다.**
2. **자동 "등록"이 아니라 자동 "채움"이다.** 브로커 조회 결과가 곧바로 `sales` 행이 되지 않는다 — 미리보기 화면에 채워 넣고 사용자가 확정해야 `Sale`이 생성된다(research 4.4-3).
3. **`Sale.allocation_id`/`Cost.subscription_id`는 NOT NULL FK인데 브로커 응답에 대응 개념이 없다.** 브로커 거래(계좌+종목코드+일시)를 우리 내부 `allocation_id`로 번역하는 매칭 로직이 이 Epic의 핵심 난제다(SF-4).
4. **`ipos.stock_code`가 NULL 허용이라 매칭이 항상 가능하지 않다.** 종목코드가 없는 IPO는 자동 채움 대상에서 자연히 제외된다 — 이건 버그가 아니라 구조적 한계로 다룬다.
5. **브로커 인증정보는 `securities_accounts`에 컬럼을 추가하지 않고 신규 테이블(`securities_account_api_credentials`)에 둔다**(research 4.8-1, 강한 권고 — 응답 DTO 오염·마스킹 규칙 충돌 방지).
6. **조회 전용은 브로커 권한이 아니라 우리 코드 구조로 강제한다** — 인터페이스에 조회 메서드만 선언 + 아웃바운드 HTTP 메서드를 GET으로 제한하는 ArchUnit 테스트가 이 Epic의 필수 산출물이다(빠지면 안 됨).

---

## 세부 기능(SF) 개요

총 **6개 SF**. research의 5단 방어 장치(A~E)와 매핑 문제 해결 순서를 그대로 따른다.

```
SF-1 (securities_firms API 메타데이터 컬럼 + 시드)
SF-2 (securities_account_api_credentials 테이블 + 암호화 키 분리) ─ SF-1과 병렬 가능
       ↓
SF-3 (KIS 조회 전용 어댑터 — 인증·잔고·체결내역, ArchUnit 방어장치 포함)
       ↓
SF-4 (브로커 거래 ↔ allocation 매칭 서비스 + 중복 판정) ─ 게이트 1 결정 필요
       ↓
SF-5 (미리보기 → 사용자 확정 → Sale 생성 API)
       ↓
SF-6 (프론트 — 계좌 연결 화면 + 동기화 미리보기 화면)
```

| SF | 내용 | 선행 SF | 게이트 필요 |
|---|---|---|---|
| SF-1 | `securities_firms` API 메타데이터 컬럼 + idempotent 시드 | 없음 | 없음 |
| SF-2 | `securities_account_api_credentials` 테이블 + 키 분리 | 없음, SF-1과 병렬 | 없음 |
| SF-3 | KIS 조회 전용 어댑터(인증·잔고·체결내역) + 조회 전용 강제 테스트 | SF-1, SF-2 | 없음(게이트 2·3 해소, 1은 필드 매핑에만 필요) |
| SF-4 | 매칭 서비스(브로커 거래 → `allocation_id`) + 중복 판정 | SF-3 | 게이트 1(사용자 결정 대기, 게이트 4는 해소) |
| SF-5 | 미리보기·확정 API (Sale 생성) | SF-4 | 없음 |
| SF-6 | 프론트 — 계좌 연결 + 동기화 미리보기 UI | SF-5 | 없음 |

---

## SF-1: `securities_firms` API 메타데이터 컬럼 + idempotent 시드

**범위**: research 4.7절 권고(B안) 그대로 채택 — `external_code`를 재사용하지 않고 신규 컬럼을 추가한다.

**산출물 파일 경로**:
- `backend/src/main/resources/db/migration/V{n}__add_securities_firm_api_columns.sql` — `api_supported BOOLEAN NOT NULL DEFAULT FALSE`, `api_provider VARCHAR(50) NULL`, `api_auth_type VARCHAR(50) NULL`, `api_readonly_only BOOLEAN NOT NULL DEFAULT TRUE`, `api_external_code VARCHAR(255) NULL`
- `domain/model/securitiesfirm/SecuritiesFirm.java` — 위 필드 추가(같은 SF에서, `ddl-auto: validate` 정합)
- `backend/src/main/resources/db/migration/V{n+1}__seed_securities_firms.sql` — `INSERT ... ON CONFLICT (name, status) DO NOTHING`, 한국투자증권만 `api_supported=TRUE, api_provider='KIS'`, 나머지는 `api_supported=FALSE`(research 4.7-4 목록, 정식 명칭 미확정 주석 명시)

**특이사항**:
- 시드 전 **기존 `INACTIVE` 행 존재 여부를 확인하는 조건부 INSERT**를 쓴다(research 4.7-5-3 — `uk_securities_firm_name_active`가 `(name, status)` 전체 유니크라 `INACTIVE` 행이 있으면 `ACTIVE` 시드가 중복 생성될 수 있음). `WHERE NOT EXISTS (SELECT 1 FROM securities_firms WHERE name = ? AND status = 'ACTIVE')` 패턴 사용.
- 기존 사용자가 만든 증권사 행은 삭제·병합하지 않는다(research 4.7-5-2, 4.7-5-4).
- 이 SF는 **시드일 뿐 "사용자 임의 증권사 생성 금지" 정책 변경이 아니다** — 등록 API 정책 변경(커스텀 허용 여부, research 6절 #17)은 이 Epic 범위 밖으로 명시하고 별도 결정 필요 항목으로 남긴다.

**AC 초안**:
- AC-1801: 신규 컬럼이 기존 행에 기본값으로 채워진 채 마이그레이션이 성공한다
- AC-1802: 시드를 2회 반복 실행해도 중복 행이 생기지 않는다(idempotent)
- AC-1803: 이미 `INACTIVE`로 동일 이름 행이 있어도 시드가 실패하거나 중복을 만들지 않는다
- AC-1804: 한국투자증권만 `api_supported=TRUE`, 나머지는 `FALSE`로 시드된다

---

## SF-2: `securities_account_api_credentials` 테이블 + 암호화 키 분리

**범위**: research 4.8-1 제안 스키마를 그대로 채택. 브로커 인증정보를 `securities_accounts`와 분리된 신규 테이블에 계좌 1:1로 저장한다.

**산출물 파일 경로**:
- `backend/src/main/resources/db/migration/V{n}__create_securities_account_api_credentials_table.sql` — research 4.8-1의 DDL(`securities_account_id`, `api_provider`, `app_key_encrypted`, `app_secret_encrypted`, `access_token_encrypted` nullable, `token_expires_at` nullable, `status`, `deleted_at`, `created_at`, `updated_at`), `UNIQUE (securities_account_id, api_provider) WHERE status='ACTIVE'`, `FK ... ON DELETE RESTRICT`
- 신규 패키지 `brokerintegration.domain.model.credential.SecuritiesAccountApiCredential` — 엔티티
- `brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository`
- `brokerintegration.infrastructure.encryption.BrokerSecretEncryptor` — **`AccountNumberEncryptor`와 동일 AES-256-GCM 구현이되, 별도 키(`app.encryption.broker-key` / `APP_ENCRYPTION_BROKER_KEY`)를 주입**(research 4.8-2 권고 — 키 분리로 단일 키 유출 시 피해 범위 제한)
- `brokerintegration.controller.credential.dto.ApiCredentialResponse` — `connected`/`expired`/`error` 상태, 마스킹된 계좌번호, 마지막 동기화 시각만 노출(**평문 시크릿·토큰 절대 미포함**)

**특이사항**:
- **계정 소유권 검증 필수**: `SecuritiesAccount`를 거치는 2단계 검증(대상의 `securitiesAccountId` → 그 `Investor.accountId` → 현재 계정 비교, CLAUDE.md에 명시된 기존 패턴 그대로).
- 토큰은 유효기간이 1일이고 재발급이 분당 1회로 제한되므로 **`access_token_encrypted`·`token_expires_at`에 암호화해 캐시하고 만료 시각 전에는 재사용**한다(게이트 3 확정). 만료 시 1회 재발급하며 동시 요청은 재발급을 한 번만 하도록 직렬화한다.
- 기존 `AccountNumberEncryptor`를 그대로 재사용하지 않고 별도 클래스로 분리하는 이유: research 4.8-2 "클래스 이름이 부정확해짐" — `AccountNumberEncryptor`의 핵심 암복호화 로직(AES-256-GCM, 랜덤 IV, `Base64(IV+ciphertext)`)을 공용 유틸로 추출하고 두 곳에서 각자의 키로 인스턴스화하는 방식을 권장(코드 중복 없이 키만 분리).
- `APP_ENCRYPTION_BROKER_KEY` 미설정 시 브로커 연결 기능 자체가 비활성화되도록 한다(research 4.8-2 — 계좌번호 암호화와 달리 "키 미설정 상태로 기동은 허용하되 연결 기능만 막는" 정책 권장).

**AC 초안**:
- AC-1810: 시크릿이 암호화되어 저장되고, DB를 직접 조회해도 평문이 보이지 않는다
- AC-1811: `ApiCredentialResponse`에 시크릿·토큰 필드가 존재하지 않는다(컴파일 타임 + 테스트로 검증)
- AC-1812: 타 계좌의 인증정보에 접근 시 403
- AC-1813: 계좌 1개에 동일 `api_provider` 인증정보가 2건 이상 생성되지 않는다
- AC-1814: `APP_ENCRYPTION_BROKER_KEY` 미설정 시 인증정보 등록 요청이 명확한 오류로 실패한다

---

## SF-3: KIS 조회 전용 어댑터 — 인증·잔고·체결내역 + 조회 전용 강제 테스트

**범위**: `brokerintegration.adapter.kis` 하위에 한국투자증권 조회 전용 클라이언트를 만든다. **이 SF의 산출물에는 어떤 형태로도 주문(매수/매도/정정/취소) 메서드가 존재하지 않는다.**

**산출물 파일 경로**:
- `brokerintegration.adapter.kis.KisAuthClient` — 앱키/앱시크릿으로 접근토큰 발급(`oauth2/tokenP` POST 1개 경로만 예외 — **토큰 발급은 POST가 불가피**하나 이후 모든 호출은 GET). 발급한 토큰은 SF-2 저장소에 만료 시각과 함께 캐시한다
- `brokerintegration.adapter.kis.KisBalanceClient` — 잔고 조회(GET)
- `brokerintegration.adapter.kis.KisTransactionHistoryClient` — 체결내역 조회(GET)
- `brokerintegration.adapter.BrokerQueryPort` — **공개 인터페이스에 조회 메서드만 선언**(research 4.3-A). 모든 어댑터(향후 확장 포함)가 이 인터페이스만 구현.
- `brokerintegration.infrastructure.config.KisApiProperties` — base URL 고정값(설정 파일에서만, 사용자 입력 불가)
- `brokerintegration.exception.BrokerApiException` — 브로커 스펙 변화가 도메인으로 새지 않도록 격리(research R5)
- **`backend/src/test/java/.../brokerintegration/architecture/BrokerReadOnlyArchitectureTest.java`** — ArchUnit(또는 이미 있는 정적 검사 도구)로 `brokerintegration.adapter` 패키지 내 `post()`/`put()`/`patch()`/`delete()` 호출이 **KisAuthClient의 토큰 발급 1건을 제외하고 0건**임을 강제하고, 추가로 소스 전체에 주문 경로 문자열(`order-cash`, `order-rvsecncl` 등 KIS 주문 엔드포인트)이 0건임을 검사한다. 이 테스트가 없으면 SF-3은 완료로 보지 않는다.

**특이사항**:
- `RestClient`(Spring 6.1 기본, `spring-boot-starter-web`에 이미 포함) 사용. `WebClient` 추가 의존성 도입 안 함(research 7-2).
- 응답 파싱은 관용적으로: 알 수 없는 필드는 무시, 없는 필드는 nullable로 받는다(research R5 — 브로커 스펙 변경에 대한 방어).
- 실계좌 없이 개발할 가능성이 높으므로(모의투자 환경 미확인, 게이트 대상 아님이지만 영향은 중간) **포트/어댑터 패턴 + WireMock 스텁으로 네트워크 호출 없는 테스트**를 우선 작성한다. 실계좌 호출은 별도 수동 검증 절차로 문서화(자동 테스트에 포함하지 않음).
- 호출 감사 로그(research 4.3-E): (시각, 계좌 ID, HTTP 메서드, 경로, 상태코드)만 기록, 시크릿 절대 미포함.

**AC 초안**:
- AC-1820: ArchUnit 테스트가 `brokerintegration.adapter` 패키지의 비-GET 호출 0건(토큰 발급 제외)을 강제하고, 위반 시 빌드가 실패한다
- AC-1821: `BrokerQueryPort` 인터페이스에 조회 메서드만 존재한다(주문 관련 메서드 시그니처 자체가 없음)
- AC-1822: 토큰 발급 실패 시 `BrokerApiException`으로 격리되고 도메인 예외로 전파되지 않는다
- AC-1823: WireMock 스텁으로 네트워크 호출 없이 전체 테스트가 통과한다
- AC-1824: 감사 로그에 시크릿·토큰 평문이 남지 않는다

---

## SF-4: 매칭 서비스 — 브로커 거래 ↔ `allocation_id` + 중복 판정

**범위**: SF-3이 조회한 체결내역을 우리 내부 `allocation_id`로 번역한다. **이 Epic에서 가장 리스크가 큰 SF다.**

**산출물 파일 경로**:
- `brokerintegration.service.matching.BrokerTransactionMatcher` — (증권계좌, 종목코드, 일자 근사) 키로 `subscriptions` → `allocations` 체인을 따라 매칭(research 4.4-2 후보 매칭 키 채택)
- `brokerintegration.service.matching.MatchResult`(+ `UnmatchedReason`) — 매칭 성공/실패(사유: 종목코드 없음/청약 기록 없음/이미 매도 등록됨 등) 결과 DTO
- (게이트 4 결과에 따라) `sales` 테이블에 **`external_transaction_id` 컬럼 추가 마이그레이션** — 중복 등록 방지용(research 4.4-2 표, 6절 #15). 브로커 응답에 거래 고유 ID가 없다면 이 컬럼은 만들지 않고 근사 매칭(계좌+종목코드+일시+수량+단가)만으로 중복 판정한다.

**특이사항**:
- `ipos.stock_code`가 NULL인 IPO는 매칭 대상에서 자연히 제외된다 — 이를 오류로 취급하지 않고 `UnmatchedReason.STOCK_CODE_MISSING`으로 명시적으로 분류해 사용자에게 "이 IPO는 종목코드가 없어 자동 매칭이 불가능하니 수동 등록하세요"로 안내한다.
- 이미 수동으로 등록된 `Sale`이 있는 배정은 매칭 결과에서 제외(중복 방지, research 4.4-3).
- **분할 매도 합산(사용자 결정 2026-09-30)**: `TTTC8715R`은 일자×종목 집계 단위이므로, 같은 날짜·같은 종목에서 우리 쪽에 여러 `Sale`로 나뉘어 기록될 수 있는 경우 브로커 집계값과 우리 `Sale`들의 합계(수량·금액·수수료·세금)를 비교해 매칭한다. 합계가 맞지 않으면 자동 채움하지 않고 `UnmatchedReason`으로 분류해 수동 확인을 안내한다.
- **이 SF는 게이트 1(수수료·세금 필드명)과 게이트 4(계좌번호 파라미터)가 해소되기 전에는 실제 필드 매핑 코드를 작성하지 않는다** — 매칭 로직(어떤 배정에 연결할지)과 필드 매핑(수수료를 어디서 가져올지)은 분리 가능하므로, 매칭 로직 뼈대는 게이트 없이도 설계 가능하나 실제 구현·테스트는 게이트 해소 후.

**AC 초안**:
- AC-1830: 종목코드·계좌·일자가 일치하는 배정으로 정확히 매칭된다
- AC-1831: `stock_code`가 NULL인 IPO는 명시적 사유로 매칭 실패 처리된다(예외 아님)
- AC-1832: 이미 `Sale`이 등록된 배정은 재매칭 대상에서 제외된다
- AC-1833: 동일 브로커 거래를 반복 조회해도 중복 매칭 결과가 생성되지 않는다

---

## SF-5: 미리보기 → 사용자 확정 → Sale 생성 API

**범위**: SF-4의 매칭 결과를 사용자가 검토·확정할 수 있는 API를 만든다. **자동 등록이 아니라 자동 채움**(research 4.4-3 — 이 원칙이 SF-5 전체의 근거).

**산출물 파일 경로**:
- `brokerintegration.controller.sync.dto.SyncPreviewResponse` — 매칭 성공 건(자동 채움된 필드 미리보기) + 매칭 실패 건(사유 포함) 목록
- `brokerintegration.controller.sync.dto.SyncConfirmRequest` — 사용자가 확정한 건의 목록(수정 가능 — 미리보기 값을 사용자가 고칠 수 있음)
- `brokerintegration.service.sync.BrokerSyncService` — `GET /securities-accounts/{id}/broker-sync/preview` (조회만, 부작용 없음) / `POST /securities-accounts/{id}/broker-sync/confirm` (확정된 건만 `SaleService.register` 호출 — 기존 `Sale` 등록 로직 재사용, 신규 등록 로직 만들지 않음)

**특이사항**:
- confirm 엔드포인트는 **기존 `SaleService`의 등록 로직을 그대로 호출**한다(Sale 생성 경로를 이원화하지 않는다 — 자동 채움도 결국 사용자가 확정한 순간 일반 Sale 등록과 동일 검증을 거쳐야 함).
- preview는 몇 번을 호출해도 부작용이 없어야 한다(순수 조회).
- 자동 채움 값에 "브로커 조회로 자동 채움" 출처 표시를 남길지는 research 6절 #18(낮음 영향도)에 따라 이번 범위에서는 UI 안내 문구 수준으로만 처리하고 별도 컬럼은 추가하지 않는다.

**AC 초안**:
- AC-1840: preview를 반복 호출해도 `sales` 테이블에 변화가 없다
- AC-1841: confirm 후에만 실제 `Sale` 레코드가 생성된다
- AC-1842: 사용자가 미리보기 값을 수정해 confirm하면 수정된 값으로 저장된다
- AC-1843: confirm 시 기존 `SaleService` 검증(중복·범위 초과 등)이 그대로 적용된다

---

## SF-6: 프론트 — 계좌 연결 화면 + 동기화 미리보기 UI

**범위**: 증권계좌 상세/설정 화면에 "증권사 API 연결" 섹션을 추가하고, 연결된 계좌에서 "동기화" 버튼으로 SF-5의 preview/confirm 흐름을 사용하는 UI를 만든다.

**산출물 파일 경로**:
- `ui/src/features/broker-connect/` — 앱키/앱시크릿 입력 폼(자체 검증만, 입력값은 절대 콘솔·로그에 남기지 않음), 연결 상태 표시(`connected`/`expired`/`error`)
- `ui/src/features/broker-sync/` — 동기화 미리보기 테이블(매칭 성공/실패 구분 표시), 값 수정 가능한 인라인 편집, 확정 버튼
- `ui/src/entities/broker-credential/types.ts`

**특이사항**:
- 앱키/앱시크릿 입력 필드는 **비밀번호 타입**으로 마스킹하고, 브라우저 자동완성/저장을 제안하지 않도록 처리한다(`autocomplete="off"`).
- "조회 전용으로만 사용됩니다"라는 안내 문구를 연결 화면에 명시적으로 노출한다(사용자 신뢰 확보, research 4.3-3의 "사용자 고지" 요구사항 반영).
- 매칭 실패 건은 왜 실패했는지(`UnmatchedReason`) 사용자가 이해할 수 있는 한국어 문구로 보여준다(예: "종목코드가 등록되지 않은 IPO입니다 — 수동으로 등록해주세요").

**AC 초안**:
- AC-1850: 앱키/앱시크릿 입력 필드가 화면에 평문으로 노출되지 않는다(타입=password)
- AC-1851: 연결 성공/실패가 명확히 구분되어 표시된다
- AC-1852: 미리보기 화면에서 값을 수정한 뒤 확정하면 수정값이 반영된다
- AC-1853: 매칭 실패 건에 대해 사람이 이해할 수 있는 실패 사유가 표시된다

---

## 참고

- **문서 의존성**: `docs/research/broker-readonly-api-pilot.md`(전체), `docs/roadmap.md` 1.1절·6절
- **모듈 소유권**: 신규 패키지 `com.ipocalc.brokerintegration`. 계좌번호 암호화 인프라(`securitiesaccountmanagement.infrastructure.encryption`)를 참조하되, 의존 방향은 spec 단계에서 확정(research 5절 제약사항 6 — 공용 `infrastructure`로 이동할지 결정 필요).
- **검증 명령**: `docs/guide-backend.md` 2절 그대로. 브로커 어댑터 테스트는 실제 네트워크 호출 없이 WireMock/스텁으로 작성(SF-3 특이사항).
- **금지사항(전 SF 공통)**: 주문(매수/매도/정정/취소) API 코드 작성 금지, 응답 DTO에 평문 시크릿·토큰 포함 금지, `Cost` 자동 채움 시도 금지(1차 범위 제외), 자동 "등록"(사용자 확인 없이 Sale 생성) 금지.
