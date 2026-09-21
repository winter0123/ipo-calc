# Work Log: securities-firm-catalog (Epic 3)

## 2026-09-21 — SF 분해 착수 전 Master 검증 (backend 워크스페이스)

### 문서 사소한 결함 수정
architecture.md의 DB 설계 표(prose)가 이미 고쳐진 실제 SQL(GENERATED ALWAYS AS IDENTITY, ON UPDATE 없음)과 달리 여전히 MySQL 표기(`AUTO_INCREMENT`, `... ON UPDATE`)를 남기고 있어 수정함. 실제 SQL 코드블록은 이미 올바른 PostgreSQL 문법이었음.

### 결정 사항 (architecture.md "결정 필요 사항" 3건에 대한 Master 판단)

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-SF-001 | (name, status) 복합 유니크 확정 | **DB 유니크 제약으로 강제(권장안 채택)** | architecture.md에 이미 설계된 그대로 확정. Service 계층 검증만으로는 동시 요청 경쟁 조건에서 중복이 생길 수 있어 DB 제약이 필요 |
| D-SF-002 | 외부코드/사업자등록번호 중복 검사 | **MVP 범위에서 검사하지 않음** | spec/architecture 모두 이 값들에 대한 중복 검사 요구사항이 없고 엣지케이스도 "null이면 제외"만 언급 — 값이 있을 때 검사한다는 요구 자체가 없으므로 검사 로직을 추가하지 않는다 |
| D-SF-003 | 조회 필터 기본값 | **deletedAt IS NULL(=ACTIVE)만 노출, INACTIVE는 목록/상세 모두 제외** | 이 Epic은 상태 전이가 ACTIVE→INACTIVE 단방향 1회뿐이고 그 순간 항상 deletedAt도 함께 설정되므로 "status=INACTIVE인데 deletedAt이 없는" 상태는 설계상 존재하지 않는다. 즉 deletedAt IS NULL 필터 하나로 ACTIVE만 자동 노출됨 (Investor의 status 파라미터 선택형 조회와 다르게, 여기는 그런 옵션이 spec에 없다) |

### 패키지 구조 결정
Investor(`com.vibeaiteam.investormanagement`)와 동일한 스타일로 **`com.vibeaiteam.securitiesfirmcatalog`** 를 루트 패키지로 사용한다. architecture.md의 "controller.securitiesfirm" 같은 표기는 하위 패키지 이름 힌트로만 참고하고, 전체 경로는 `com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm` 형태로 구성한다.

### SF 분해 (5.4, 총 5개, 순차 의존)
Investor(SF-1 공용 인프라 포함 6개)와 달리, 이 Epic은 계정 소유권 검증이 필요 없는 글로벌 기준정보라 SecurityUtils를 쓰지 않는다. 공용 인프라 SF 없이 바로 도메인 SF부터 시작한다.

1. **SF-1: SecuritiesFirm 엔티티/Repository/마이그레이션** — BIGINT IDENTITY, (name, status) 복합 유니크 인덱스, `com.vibeaiteam.securitiesfirmcatalog` 패키지 신설
2. **SF-2: 증권사 등록 API** (POST /securities-firms) — AC-001~003
3. **SF-3: 증권사 목록/상세 조회 API** (GET /securities-firms, GET /securities-firms/{id}) — AC-004~006, D-SF-003 필터 적용
4. **SF-4: 증권사 수정 API** (PATCH /securities-firms/{id}) — AC-007~010
5. **SF-5: 증권사 비활성화 API** (DELETE /securities-firms/{id}, 논리 삭제) — AC-011~013
