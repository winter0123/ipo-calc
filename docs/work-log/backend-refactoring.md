# Work Log: backend-refactoring (2026-10-01)

사용자 승인 아래 백엔드를 동작 불변으로 정리한다. 직접 구현·병합한다.

## 원칙

- API 상태 코드·에러 코드·메시지, DB 스키마는 바꾸지 않는다. 에러 코드 통일(`VALIDATION_FAILED`/`VALIDATION_ERROR`)은 API 계약 변경이라 제외한다.
- 단계 하나가 PR 하나. 매 단계 슬롯 DB에서 `test --rerun`(테스트 개수 유지), `checkstyleMain checkstyleTest --rerun-tasks` 0건을 확인하고, 2단계와 마지막에는 E2E·axe도 돌린다.
- 건드리지 않는 것: `ponytail:` 주석으로 남긴 의도적 단순화 4건(토큰 락, 단가 근사, 메모리 제한기, 전체 행 재암호화), KIS 어댑터 동작, 프론트엔드.

## 기준선 (단계 0, 2026-10-01)

| 항목 | 값 |
|---|---|
| 백엔드 테스트 | 754건 통과(구조 테스트 2건 포함) |
| checkstyle | 0건 |
| 에러 핸들러 | 14개 파일, `@ExceptionHandler` 57개, `JSON_UTF8` 상수 14곳 중복 |
| 소유권 검증 | `findOwnedX` 서비스 7개(Investor·Cost·Sale·Allocation·Subscription 등) + `DashboardOwnershipVerifier`·`InvestmentHistoryOwnershipVerifier`, 경로 해석이 여러 곳 |
| 250줄 초과 서비스 | `ApiCredentialService` 357, `IpoSyncService` 337, `SecuritiesAccountService` 291, `MarketHolidaySyncService` 291, `IpoService` 286, `BrokerSyncService` 281, `SaleService` 280, `SubscriptionService` 275 등 |
| service → 다른 패키지 `controller.dto` 의존 | 12개 파일(이번에는 증권사 연동만 정리) |
| 컨트롤러 테스트의 로그인 준비 코드 중복 | 17개 파일 |

## 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| 0 | 안전망: 패키지 순환 금지·저장소/엔티티의 controller 비의존을 `PackageStructureTest`로 고정 | 완료 |
| 1 | 에러 응답 공통화(`ErrorResponses`) | 예정 |
| 2 | 소유권 검증 중앙화(경로 해석 한 곳, 표 기반 격리 테스트 선행) | 예정 |
| 3 | 증권사 연동 정리(`ApiCredentialService` 분해, `MatchCandidateAssembler`, DTO를 service 쪽으로) | 예정 |
| 5 | 컨트롤러 테스트 공통 도우미 | 예정 |
| 4·6 | 큰 서비스 재평가, 설정 일원화(선택) | 필요 시 |
