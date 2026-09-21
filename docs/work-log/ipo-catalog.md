# Work Log: ipo-catalog (Epic 5)

## 2026-09-21 — SF 분해 착수 전 Master 검증 (backend 워크스페이스)

### 결정 사항 (architecture.md "결정 필요 사항" 4건에 대한 Master 판단)

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-IPO-001 | 중복 검사 이중화 확정 | **Service 계층 검증(1차 방어) + DB partial unique index(보조 방어) 모두 적용** (architecture.md 권장안 채택) | Epic 3(SecuritiesFirm)의 (name, status) 복합 유니크 패턴과 동일한 이중 방어 원칙. stock_code 유무에 따라 분기하는 로직은 Service 계층에서 공용 헬퍼로 구현해 등록/수정/CSV 행별 3개 진입점에서 재사용한다 |
| D-IPO-002 | CSV 파일 크기 상한 | **5MB 확정** (architecture.md 제안값 그대로 채택) | `spring.servlet.multipart.max-file-size=5MB`로 적용. MVP 규모(수백~수천 행)에 충분하고 대용량 처리는 명시적으로 범위 밖 |
| D-IPO-003 | CSV 파서 라이브러리 | **`org.apache.commons:commons-csv` 신규 의존성 추가** | spec 엣지케이스가 "특수 문자(콤마, 큰따옴표)가 포함된 기업명/공모주명 처리"를 명시하는데, 쉼표 기준 단순 split으로는 따옴표로 감싼 필드 안의 쉼표를 안전하게 처리할 수 없다. commons-csv는 RFC 4180 호환 파싱과 헤더 검증을 표준으로 제공해 자체 파서보다 안전하다. 프로젝트에 CSV 요구사항이 이번이 처음이라 기존 관례가 없으므로 신규 도입 |
| D-IPO-004 | 조회 필터 기본값 | **목록/상세 조회는 `deleted_at IS NULL` 기준으로만 필터한다 (ACTIVE/INACTIVE 상태값과 무관하게 존재 여부로 필터)** | architecture.md의 비활성화 흐름 자체가 "status를 INACTIVE로 변경 **+ deletedAt 설정**"을 항상 동시에 수행하도록 설계돼 있어(Epic 3/4와 동일 패턴), `deletedAt IS NULL` 필터 하나로 INACTIVE 레코드가 자동으로 함께 제외된다. status와 deletedAt을 별도로 검사할 필요가 없다 |

### 계정 소유권 검증 불필요 (SecuritiesFirm과 동일한 글로벌 기준정보)

Ipo는 Account 소유 엔티티가 아니라 모든 인증된 사용자에게 동일하게 노출되는 글로벌 기준정보다. `SecurityUtils.getCurrentAccountId()` 기반 소유권 검증은 적용하지 않는다 (architecture.md 명시).

### 패키지 구조 결정

`com.vibeaiteam.ipocatalog`를 루트 패키지로 사용 (Investor/SecuritiesFirm/SecuritiesAccount와 동일한 명명 스타일).

### SF 분해 (5.4, 총 6개, 순차 의존)

```
SF-1 (엔티티/Repository/마이그레이션) → SF-2 (등록, 중복 검사 헬퍼 신설)
  → SF-3 (목록/상세 조회) → SF-4 (수정) → SF-5 (비활성화) → SF-6 (CSV 일괄 등록)
```

1. **SF-1**: `Ipo` 엔티티(BIGINT IDENTITY), Repository, V5 마이그레이션 — partial unique index 2개(`uk_ipo_stock_code_active`, `uk_ipo_name_start_date_active`)는 별도 `CREATE UNIQUE INDEX` 문
2. **SF-2**: POST /ipos — 필수값/날짜순서/가격범위/255자 검증(400), Service 계층 중복 검사 헬퍼 신설(D-IPO-001, stock_code 유무 분기), 중복 시 409
3. **SF-3**: GET /ipos, GET /ipos/{id} — `deletedAt IS NULL` 필터(D-IPO-004), 목록은 createdAt 내림차순
4. **SF-4**: PATCH /ipos/{id} — 제공된 필드만 수정, 자기 자신 제외 중복 검사(SF-2 헬퍼 재사용), 404/400/409
5. **SF-5**: DELETE /ipos/{id} — 논리 삭제(status=INACTIVE + deletedAt), 연결된 Subscription 여부와 무관하게 항상 허용(spec AC-014/AC-015)
6. **SF-6**: POST /ipos/import — commons-csv 신규 의존성(D-IPO-003), 헤더 검증(400), 행 단위 개별 저장(한 행 실패가 나머지에 영향 없음, AC-019), SF-2 중복 검사 헬퍼 재사용, successCount/failureCount/failures[] 집계
