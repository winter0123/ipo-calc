# 증권계좌 서비스 소유권 경계 조사

## 기준과 관찰

기준 코드 `SecuritiesAccountService`는 계좌 CRUD/default 규칙과 함께 `requireOwner` 및 `requireOwnedInvestor`에서 investor 조회 후 `OwnershipGuard`를 호출한다. 계좌 단건 요청은 계좌가 없는 경우 404, 투자자가 없으면 계좌를 찾을 수 없음으로 404, 타 계정이면 계좌 도메인의 403을 반환한다. 투자자 목록 요청은 investor 미존재 404와 타 계정 403을 구분한다.

서비스의 소유권 확인은 register/list에서 investor ID로, get/update/deactivate/setDefault/getAccountNumber에서 account→investor 경로로 수행한다. 평문 계좌번호는 암호화 저장, 조회 DTO에서는 마스킹, 전체 번호 공개는 소유권 확인·감사 로그 후 수행한다.

## 제안

현재 2단계 소유권 처리를 `SecuritiesAccountOwnershipVerifier`로 이동해 서비스에서 반복되는 보안 분기를 한 경계에 모은다. repository 조회 방식과 예외 변환 순서는 바꾸지 않는다. default 정책, 계좌 암호화, CRUD, 조회 매핑은 이번 추출 범위에서 제외한다.

## 검증 위험

403/404 혼동, investor ownership 확인 누락, 전체 번호 노출 전 검증 누락, 기존 Spring 테스트의 mock wiring 문제가 핵심 위험이다. 소유권 예외 및 account access 테스트를 통해 확인한다.
