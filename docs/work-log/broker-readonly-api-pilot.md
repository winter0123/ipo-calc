# Work Log: broker-readonly-api-pilot (Epic 18)

## 2026-09-30 — Master 결정·검증 기록

### 결정 사항

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-B-001 | 매도·수수료·세금 자동 채움 소스 | 기간별매매손익현황 `TTTC8715R` | 수수료·세금은 이 API에만 있다. 일자×종목 집계이므로 같은 날 같은 종목 분할매도는 합산해 배정 후보와 매칭한다(SF-4a) |
| D-B-002 | 실계좌 KIS 호출 | SF-3 이후 Master가 1회만 수행. 키는 사용자가 `.env`에 넣고 로그·커밋·보고서에 남기지 않는다 | `prsm_tlex_smtl` 의미, 공모주 배정분 표시, `pdno` 형식 확인 목적. 사용자가 키를 아직 발급하지 못해 대기 |
| D-B-003 | 조회 전용의 의미 | 읽기 전용 키 확인은 불필요, 주문·정정·취소 코드를 만들지 않는다 | 사용자 지시. ArchUnit(`BrokerReadOnlyArchitectureTest`)으로 비 GET 호출은 토큰 발급 POST 하나뿐임을 강제 |
| D-B-004 | 자동 채움 성격 | 자동 등록이 아닌 미리보기→사용자 확정→Sale 생성 | 잘못 매칭된 거래가 조용히 저장되는 것을 막는다 |
| D-B-005 | 토큰 | 만료 5분 전 갱신, 암호화해 캐시, 자격증명별 락 | 다중 인스턴스에서는 락이 프로세스 로컬이라는 한계를 `ponytail:` 주석으로 남김 |
| D-B-006 | 기능 비활성 조건 | `APP_ENCRYPTION_BROKER_KEY` 미설정이면 등록 API가 `BrokerConnectionUnavailableException` | 키 없이 평문 저장하는 경로를 만들지 않는다 |

### 병합 진행

SF-1(#204) → SF-2a(#205) → SF-3a(#210) → SF-3b(#212) → SF-4a(#207) → SF-2b(#214). Master 검증: SF-2b는 전체 백엔드 테스트 650개 통과, checkstyle 경고 0(`--rerun-tasks`), 서비스·컨트롤러·DTO를 직접 읽어 비밀값이 응답·`toString`에 나오지 않고 소유권이 증권계좌→투자자→계정 2단계로 검증됨을 확인.

### 남은 것

- SF-4b(실제 응답 필드 매핑, 필요 시 `sales.external_transaction_id` 마이그레이션), SF-5(미리보기→확정 API), SF-6(프론트 두 화면)은 KIS 키와 실계좌 1회 호출 결과가 있어야 필드 매핑을 확정할 수 있다.
- 알려진 사소한 부정확: V16 SQL 주석의 "1:1"이 실제와 다르나 Flyway 체크섬 때문에 고치지 않는다.

## 2026-10-01 실계좌 1회 조회 결과(SF-4b)

Master가 실계좌로 `TTTC8715R`를 1회 호출했다. 계좌번호·키·토큰·실제 금액·종목명은 기록하지 않는다(형태만).

### 확정된 응답 형태

| 항목 | 확인 결과 |
|---|---|
| 결과 코드 | `rt_cd="0"`, `msg_cd="KIOK0510"` |
| 최상위 키 | `rt_cd`, `msg_cd`, `msg1`, `output1`(배열), `output2`(객체), `ctx_area_fk100`, `ctx_area_nk100` |
| `output1` 한 행 | (`trad_dt`, `pdno`) 하루×종목 집계 1건 |
| `output1` 필드 | `trad_dt`(yyyyMMdd), `pdno`, `prdt_name`, `trad_dvsn_name`, `loan_dt`(빈 문자열), `hldg_qty`, `pchs_unpr`, `buy_qty`, `buy_amt`, `sll_pric`, `sll_qty`, `sll_amt`, `rlzt_pfls`, `pfls_rt`(소수 8자리 문자열), `fee`, `tl_tax`, `loan_int` |
| 값 타입 | 전부 JSON 문자열. 수량·금액·`fee`·`tl_tax`는 부호·소수점 없는 정수 문자열(`"0"` 포함) |
| `pdno` | 6자리이나 숫자만이 아니다(숫자 5개+영문 1개 같은 영숫자 코드 존재). `ipos.stock_code`와 문자열 그대로 비교 |
| 매수·매도 | 한 행에 `buy_*`와 `sll_*`가 함께 있다. 매도가 있는 행은 `sll_qty > 0` |
| `output2` | `prsm_tlex_smtl` 필드가 없다(DTO에는 있으나 항상 null). 매핑에서 쓰지 않는다 |
| 수수료·세금 | `fee`·`tl_tax`가 `output1` 행마다 있다 |

### 결정

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-B-007 | 매핑 규칙 | `KisTradeProfitMapper`: ① `sll_qty`가 0·빈 값인 행 제외 ② 같은 (`trad_dt`,`pdno` 문자열) 행은 수량·금액·수수료·세금 합산 ③ 금액=`sll_amt`, 수수료=`fee`, 세금=`tl_tax`(빈 값은 0) ④ 매도 행의 일자·`pdno`·수량·금액이 해석 불가면 `BrokerApiException("invalid_response")`(메시지에 필드명만) ⑤ 일자→`pdno` 오름차순 | 응답에 거래 고유 ID가 없으므로 `sales.external_transaction_id` 마이그레이션은 만들지 않고 breakdown의 근사 매칭 경로를 채택한다 |
