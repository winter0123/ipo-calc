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
| 1 | 에러 응답 공통화(`ErrorResponses`): 핸들러 13개 파일에서 `JSON_UTF8` 상수 14곳과 `response()` 헬퍼·인라인 빌더를 `ErrorResponses.of`/`validationFailed`로 교체(순삭제 약 134줄), 응답 상태·Content-Type·에러 코드·메시지 불변. 남긴 것: 필드 오류가 없는 `Retry-After` 헤더 응답 1건 | 완료 |
| 2 | 소유권 검증 중앙화: `OwnershipErrors`(도메인 예외 팩토리)·`OwnershipGuard` 오버로드, `SubscriptionOwnership`·`AllocationOwnership`(청약·배정 경로를 푸는 코드를 한 곳에). Cost·Sale·Allocation·Subscription 서비스의 `findOwnedX`·`requireOwnedAllocation`·`validate*Ownership`를 이것으로 교체(서비스 4개 순삭제 약 35줄, 이전 구현과 같은 조회 방식(`findById` vs 삭제 제외)·예외 클래스·메시지 유지). 패키지 순환이 생기지 않도록 청약·배정 각 패키지 아래에 둠. 두 클래스의 단위 테스트 10건 추가 | 완료 |
| 3 | 증권사 연동 정리: `ApiCredentialAssembler`(응답 조립·연결 상태·만료일 계산)와 `MatchCandidateAssembler`(청약·배정·매도·공모주를 모아 매칭 후보 생성)를 분리. `ApiCredentialService` 357→279줄, `BrokerSyncService` 281→202줄, 서비스의 `securitiesAccountId` null 검사 4곳을 `requireId`로 통합. 응답·예외·로그·트랜잭션 불변 | 완료 |
| 5 | 컨트롤러 테스트 공통 도우미: 11개 파일의 `signupAndLogin` 복붙을 `AuthTestSupport`(`signup`/`login`/`signupAndLogin`)로 통합(순삭제 약 240줄). 관리자 승격·계정 ID 조회처럼 가입과 로그인 사이에 끼는 동작은 순서 그대로 | 완료(#299) |
| 4·6 | 큰 서비스 재평가, 설정 일원화(선택) | 필요 시 |

## 2단계 메모 (2026-10-01)

- **계획과 달라진 점**: `DashboardOwnershipVerifier`·`InvestmentHistoryOwnershipVerifier`는 바꾸지 않았다. 이 둘은 "해당 투자자를 찾을 수 없습니다."·"해당 데이터에 접근 권한이 없습니다."처럼 **다른 메시지**를 쓰고 화면에 그대로 보이는 API 응답이라, 통합하면 응답 문구가 바뀐다(동작 불변 원칙 위반). 메시지를 통일하는 것은 별도 승인이 필요한 API 계약 변경이다.
- **구조 판단**: 소유권 경로를 `OwnershipGuard` 하나에 모으면 `investormanagement`가 청약·배정 저장소에 의존해 패키지 순환이 생긴다(`PackageStructureTest`가 막는다). 그래서 청약·배정 각 패키지 아래에 한 단계씩 둔다.
- **안전망**: 기존 `OwnershipIsolationTest`와 컨트롤러 테스트(타 계정 403·없음 404)가 그대로 통과했고, 새 단위 테스트가 표준 메시지·삭제 제외 여부를 고정한다.

## 3단계 메모 (2026-10-01)

- **DTO를 service 쪽으로 옮기는 항목은 하지 않았다.** `controller.dto` 패키지를 옮기면 모든 import와 테스트가 바뀌고 이득은 없다(서비스→컨트롤러 DTO 의존은 12개 파일에 퍼져 있어 증권사 연동만 옮기면 오히려 일관성이 깨진다). 별도 계획이 있을 때 일괄로 한다.
- **워커 시도와 폐기**: OpenCode 워커(Nemotron 3 Super)에 같은 설계로 맡겼으나 테스트 파일의 `import` 키워드를 지워 컴파일이 깨지고, 조립기를 mock으로 바꿔 테스트가 동작 검증을 하지 않게 됐으며, 주 코드에도 미사용 의존성·Javadoc 오타·불필요한 import가 있었다. 사용자 지시로 코디네이터가 직접 같은 설계로 다시 구현했다(워커 산출물은 폐기).
- **5단계 구조 판단**: 11개 중 4개만 완전히 같은 본문이고 나머지는 가입 후 계정 조회·관리자 승격이 끼어 있다. 공통 부분(가입 호출, 로그인 호출)만 정적 메서드로 모으고, 끼어드는 동작은 각 테스트에 남겼다.
