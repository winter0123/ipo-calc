# 증권계좌 소유권 검증 아키텍처

```text
SecuritiesAccountService
  ├─ 계좌 존재/상태 확인 (SecuritiesAccountRepository)
  └─ SecuritiesAccountOwnershipVerifier
      ├─ Account → Investor 조회 및 누락 시 기존 not-found 예외
      ├─ Investor ID → Investor 조회 및 누락 시 기존 not-found 예외
      └─ OwnershipGuard로 현재 로그인 계정 확인 및 계좌 403 변환
```

Verifier는 `SecuritiesAccount` 응답/도메인 상태를 수정하지 않는다. `getAccountNumber`에서는 verifier의 성공 반환 후에만 decrypt와 감사를 수행한다. 암호화/마스킹 및 default-account transaction 흐름은 기존 service에 남긴다.
