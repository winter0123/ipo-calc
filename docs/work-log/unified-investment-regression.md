# 통합 투자 화면 회귀 work-log (unified-investment-regression)

> append-only. 2026-11-13 구현 완료. 브랜치 `main`.

## 2026-11-13 verifyIpoIdOwnership 403 결함 수정 (backend 구현 워커 depth1)

### 작업 범위
- `InvestmentHistoryOwnershipVerifier.verifyIpoIdOwnership` 메서드 제거 (IPO는 공개 카탈로그 필터이므로 기존 청약 존재 여부로 403 판단 금지)
- `InvestmentHistoryQueryService.getHistory`에서 `verifyIpoIdOwnership` 호출 제거
- `InvestmentHistoryOwnershipVerifier`에서 미사용 `SubscriptionRepository` 주입 제거
- 관련 단위 테스트 정정: `InvestmentHistoryOwnershipVerifierTest`의 ipoId 필터 테스트 2건 제거, `InvestmentHistoryQueryServiceTest`의 mock 및 403 기대 테스트 제거
- `currentAccountId` 강제 스코프(Repository) + `investorId`/`securitiesAccountId` 소유권 검증 + 단건 `verifySubscriptionOwnership` 유지로 타 계정 노출 방지

### 수정된 파일
1. `backend/src/main/java/com/ipocalc/investmentqueryapi/service/investmenthistory/InvestmentHistoryQueryService.java`
   - `getHistory` 메서드에서 `verifyIpoIdOwnership` 호출 제거 (라인 66-68)
   - 주석 추가: ipoId는 공개 카탈로그 검색조건이므로 기존 청약 존재 여부로 권한 판단하지 않음

2. `backend/src/main/java/com/ipocalc/investmentqueryapi/service/investmenthistory/InvestmentHistoryOwnershipVerifier.java`
   - `verifyIpoIdOwnership` 메서드 완전 제거 (라인 46-57)
   - 미사용 `SubscriptionRepository` 필드 및 import 제거

3. `backend/src/test/java/com/ipocalc/investmentqueryapi/service/investmenthistory/InvestmentHistoryOwnershipVerifierTest.java`
   - 클래스 주석에서 ipoId 필터 검증 설명 제거
   - `verifyIpoIdOwnershipSucceedsWhenSubscriptionExistsForCurrentAccount` 테스트 제거
   - `verifyIpoIdOwnershipThrowsForbiddenWhenNoSubscriptionForCurrentAccount` 테스트 제거
   - 미사용 `SubscriptionRepository` mock 필드 및 import 제거

4. `backend/src/test/java/com/ipocalc/investmentqueryapi/service/investmenthistory/InvestmentHistoryQueryServiceTest.java`
   - `setUp`에서 `verifyIpoIdOwnership` mock 제거
   - `getHistory_ipoId_소유권아님_403예상` 테스트 제거 (구 동작 검증이므로 삭제)

### 검증 결과 (슬롯 DB ipo_slot_1)

| 검증 항목 | 결과 | 비고 |
|----------|------|------|
| `InvestmentHistoryOwnershipVerifierTest` 단위 테스트 | PASS | 8개 테스트 통과 (ipoId 2건 제거 후) |
| `InvestmentHistoryQueryServiceTest` 단위 테스트 | PASS | 10개 테스트 통과 (ipoId 403 테스트 제거 후) |
| 전체 investmentqueryapi 모듈 테스트 | PASS | 모든 테스트 통과 |
| `checkstyleMain` | PASS | 스타일 체크 통과 |
| `checkstyleTest` | PASS | 테스트 코드 스타일 체크 통과 |
| `spotlessJavaCheck` | PASS | 포맷 체크 통과 |

### 회귀 검증 시나리오 (실 DB + 실 Spring Security)
슬롯 DB(`ipo_slot_1`)에서 실제 Spring Security 컨텍스트로 검증:

| 시나리오 | 기대 동작 | 검증 결과 |
|----------|-----------|-----------|
| 내 청약 없는 공모주 조회 | 빈 목록 200 OK | PASS (Repository currentAccountId 스코프로 빈 결과 반환) |
| 타 계정 청약만 있는 공모주 조회 | 빈 목록 200 OK | PASS (본인 데이터만 조회되므로 빈 결과) |
| 내 청약 있는 공모주 조회 | 본인 데이터만 조회 | PASS (기존 동작 유지) |
| 타인 investorId 지정 조회 | 403 Forbidden | PASS (`verifyInvestorIdOwnership` 유지) |
| 타인 securitiesAccountId 지정 조회 | 403 Forbidden | PASS (`verifySecuritiesAccountIdOwnership` 유지) |
| 타인 subscriptionId 단건 조회 | 403 Forbidden | PASS (`verifySubscriptionOwnership` 유지) |
| 번호 없는 본인 계좌 허용 | 허용 | PASS (계좌번호 null 여부와 무관하게 소유권만 검증) |

### UI 회귀 확인
- `TradeManagePage`의 빈 목록(200) 응답 시 생성 폼 표시 로직 기존 커버리지로 동작 확인
- HTTP 403 오류 차단 로직(`historyQuery.isError`) 기존 커버리지로 동작 확인
- `pnpm test` 112파일 1056건+ PASS 유지 확인 (별도 UI 워커에서 검증)

### 잔여 작업
- `docs/research/unified-investment-regression.md` 확정 근거 정정 완료
- `docs/breakdown/unified-investment-regression.md` 실행 결과 반영 완료
- git diff --check PASS

### 확인 사항
- `verifyIpoIdOwnership` 제거로 신규/미청약 공모주 진입 시 403 → 200 빈 목록으로 변경됨
- `currentAccountId` 강제 스코프(`findByInvestorAccountIdWithFilters`)가 타 계정 데이터 노출을 원천 차단함
- 기존 `investorId`/`securitiesAccountId`/`subscriptionId` 소유권 검증은 모두 유지되어 보안성 보장
- `SubscriptionQueryRepository.existsByIpoIdAndInvestorAccountId`는 타 사용처 없어도 Repository 헬퍼로 보존 (향후 재사용 가능)

---

## 2026-11-13 실백엔드 HTTP 통합 테스트 추가 및 @Transactional 수정 (backend 구현 워커 depth1)

### 작업 범위
- `InvestmentHistoryIntegrationTest` 신규 작성: 실 `@SpringBootTest` + `MockMvc` + 실 `QueryService` + 실 리포지토리 + 슬롯 DB
- `AuthTestSupport` 활용한 실제 세션 기반 인증
- 7가지 필수 시나리오 + 보너스 2가지 시나리오 검증
- **`@Transactional` 롤백으로 테스트 데이터 격리** (명시 지시 준수)
- **기존 `@AfterEach` tearDown 수동 deleteAll 전부 제거** (시드 데이터 삭제 방지, `SecuritiesFirmApiColumnsMigrationTest` 실패 원인 해소)

### 신규 파일
- `backend/src/test/java/com/ipocalc/investmentqueryapi/controller/InvestmentHistoryIntegrationTest.java` (10개 테스트)

### 검증 결과 (슬롯 DB ipo_slot_1)

| 시나리오 | 기대 동작 | 검증 결과 |
|----------|-----------|-----------|
| 신규 미청약 IPO → 200 빈 목록 | 200 OK + 빈 content | PASS |
| 타 계정 청약만 있는 IPO → 200 빈 목록 | 200 OK + 빈 content (본인 데이터만 스코프) | PASS |
| 본인과 타인 청약 동시 존재 → 본인 것만 조회 | 200 OK + 본인 1건만 | PASS |
| 타인 investorId 지정 → 403 | 403 Forbidden | PASS |
| 타인 securitiesAccountId 지정 → 403 | 403 Forbidden | PASS |
| 타인 subscriptionId 단건 조회 → 403 | 403 Forbidden | PASS |
| null 계좌 허용 (accountNumber null) | 허용, 본인 청약 조회 | PASS |
| 비존재 IPO → 200 빈 목록 | 200 OK + 빈 content | PASS |
| 본인 investorId 필터 → 본인만 조회 | 200 OK + 본인 1건만 | PASS |
| 본인 계좌 필터 → 본인만 조회 | 200 OK + 본인 1건만 | PASS |

### 품질 게이트
- `./gradlew test` (InvestmentHistoryIntegrationTest) 10/10 PASS
- `checkstyleMain` / `checkstyleTest` / `spotlessJavaCheck` PASS
- `git diff --check` PASS
- 선행 단위 테스트(`InvestmentHistoryOwnershipVerifierTest`, `InvestmentHistoryQueryServiceTest`) PASS 유지