# Work Log: securities-account-management (Epic 4)

## 2026-09-21 — SF 분해 착수 전 Master 검증 (backend 워크스페이스)

### 문서 사소한 결함 수정
architecture.md의 DB 설계 표(prose)가 이미 고쳐진 실제 SQL(GENERATED ALWAYS AS IDENTITY, ON UPDATE 없음)과 달리 여전히 MySQL 표기(`AUTO_INCREMENT`, `... ON UPDATE`)를 남기고 있어 수정함. 실제 SQL 코드블록(V4 마이그레이션 제안)은 이미 올바른 PostgreSQL 문법 + partial unique index 분리였음.

### 결정 사항 (architecture.md "결정 필요 사항" 5건 + 추가 발견 2건에 대한 Master 판단)

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-SA-001 | 암호화 알고리즘 확정 | **AES-256-GCM, JDK 내장 `javax.crypto` 사용** (권장안 채택) | 추가 의존성 불필요, MVP에 충분 |
| D-SA-002 | MySQL 호환성 | **불필요 — 이 프로젝트는 PostgreSQL 전용** | 이미 Epic 1~3 전체가 PostgreSQL 전용 문법(GENERATED ALWAYS AS IDENTITY, partial unique index 등)으로 확정돼 있음. MySQL 대응 코드/문서는 작성하지 않는다 |
| D-SA-003 | 기본 계좌 자동 지정 정책 | **투자자의 첫 번째 계좌 등록 시 isDefault=true 자동 설정** (spec 기본값 그대로 채택) | 등록 시점에 해당 투자자의 기존 계좌 수를 확인해 0건이면 요청의 isDefault 값과 무관하게 true로 강제한다 |
| D-SA-004 | 비활성화된 기본 계좌 처리 | **DELETE(비활성화) 시 대상 계좌가 is_default=true였다면 함께 false로 clear한다** | architecture.md 리스크 #5가 지적한 "INACTIVE인데 is_default=true" 상태를 애초에 발생시키지 않는 편이 "is_default=true인 레코드는 항상 status=ACTIVE"라는 불변식을 유지해 이후 SF-7(기본 계좌 지정)의 조회 로직을 단순하게 만든다. 이 경우 투자자는 일시적으로 기본 계좌가 없는 상태가 되며, 재지정은 사용자가 별도로 set-default를 호출해야 한다(자동 재지정 없음, spec에 요구사항 없음) |
| D-SA-005 | 감사 로그 구현 방식 | **별도 DB 테이블 없이 SLF4J 애플리케이션 로그로 기록** | 로드맵 전체 어디에도 AuditLog 엔티티가 설계된 바 없고, 개인용 MVP 도구 범위에서 별도 감사 테이블은 과설계. `log.info("계좌번호 노출: accountId={}, requestedByAccountId={}, at={}", ...)` 형태로 충분 |
| D-SA-006 | 기본 계좌 동시성 제어 방식 | **명시적 락(@Version/SELECT FOR UPDATE) 없이 단순 `@Transactional`만 사용** | partial unique index(D-SF 계열과 동일 원리)가 DB 레벨 최종 무결성을 보장하고, 개인용 도구라 실질적 동시 요청 경쟁이 없다. 유니크 위반이 실제로 발생하면 `DataIntegrityViolationException`을 잡아 409로 변환하는 방어 코드만 추가한다 — 낙관적 락 컬럼(@Version)은 엔티티에 추가하지 않는다 |
| D-SA-007 | DELETE(비활성화) 시 금융 기록 참조 차단 여부 | **차단하지 않는다 — architecture.md 데이터 흐름 4단계의 "연결된 Subscription 있으면 400" 문구는 채택하지 않는다** | 이 문구는 spec.md의 AC-012(정상 비활성화, 차단 조건 없음)와 모순되고, Epic 6(청약 기록)이 아직 구현되지 않아 실제로 검증할 대상 테이블도 없다. Account/Investor/SecuritiesFirm Epic 전체에서 일관되게 적용해온 "논리 삭제는 항상 허용, 물리 삭제만 금지" 원칙을 그대로 따른다. AC-013("비활성화된 계좌는 신규 청약 불가, 기존 기록 유지")은 Epic 6이 청약 생성 시점에 계좌 status를 확인하는 것으로 구현될 문제이며, 이 Epic의 DELETE 책임이 아니다 |

### 소유권 검증 패턴 (Investor와 다름, 2단계 확인 필요)
Investor Epic은 `SecurityUtils.getCurrentAccountId() == investor.accountId`로 1단계 확인이었지만, 이 Epic은 **SecuritiesAccount → investorId → Investor.accountId** 2단계 확인이 필요하다:
```
Long currentAccountId = securityUtils.getCurrentAccountId();
Investor investor = investorRepository.findById(securitiesAccount.getInvestorId())...;
if (!currentAccountId.equals(investor.getAccountId())) throw Forbidden;
```
POST(등록) 시에는 요청 본문의 `investorId`가 현재 계정 소유인지를 먼저 검증한다(AC-005).

### 패키지 구조 결정
`com.vibeaiteam.securitiesaccountmanagement`를 루트 패키지로 사용 (Investor/SecuritiesFirm과 동일한 명명 스타일).

### SF 분해 (5.4, 총 7개, 순차 의존)
```
SF-1 (엔티티/Repository/마이그레이션/암호화 유틸) → SF-2 (등록) → SF-3 (목록/상세 조회)
  → SF-4 (계좌번호 전체 노출) → SF-5 (수정) → SF-6 (비활성화) → SF-7 (기본 계좌 지정)
```

1. **SF-1**: `SecuritiesAccount` 엔티티(BIGINT IDENTITY, investorId/securitiesFirmId FK), Repository, V4 마이그레이션(partial unique index 별도 `CREATE UNIQUE INDEX` 문), `AccountNumberEncryptor`(AES-256-GCM, `infrastructure.encryption` 패키지), 마스킹 유틸리티(뒷 4자리 노출)
2. **SF-2**: POST /securities-accounts — investorId 소유권 검증(AC-005), 첫 계좌 자동 isDefault=true(D-SA-003), 암호화 저장, 마스킹 응답
3. **SF-3**: GET /securities-accounts, GET /securities-accounts/{id} — 마스킹, securitiesFirmName 조인, 2단계 소유권 검증
4. **SF-4**: GET /securities-accounts/{id}/account-number — 복호화 평문 반환, SLF4J 감사 로그(D-SA-005)
5. **SF-5**: PATCH /securities-accounts/{id} — accountName/securitiesFirmId만 수정, accountNumber 불변, INACTIVE 증권사로 변경 금지
6. **SF-6**: DELETE /securities-accounts/{id} — 논리 삭제, is_default였다면 함께 clear(D-SA-004), 참조 차단 없음(D-SA-007)
7. **SF-7**: PATCH /securities-accounts/{id}/set-default — 기존 기본 계좌 해제 + 신규 지정 원자적 처리, 단순 @Transactional(D-SA-006), 이미 기본이면 409, 비활성 계좌면 400
