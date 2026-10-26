# 증권계좌 소유권 경계 Breakdown

## SF-1 — 소유권 조회와 예외 변환 verifier 추출

- 범위: 새 `SecuritiesAccountOwnershipVerifier`, `SecuritiesAccountService` 호출부, 기존 계좌 service 테스트.
- 비범위: 계좌 CRUD/default 정책, 암호화·마스킹, API/DB 변경.
- AC: 관련 spec 5개 조건을 만족하며, 타 계정 403·없는 resource 404·계좌번호 복호화 전 ownership 확인이 기존과 같다.
- 검증: 단계가 완료된 뒤 계좌 관련 테스트와 마지막 통합 backend 검증에서 확인한다. 테스트 중 외부 API 호출은 없다.
- 완료 보고: 실제 파일/검증 결과와 미해결 사항을 work-log/current-status에 기록한다.
