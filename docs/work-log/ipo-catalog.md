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

`com.ipocalc.ipocatalog`를 루트 패키지로 사용 (Investor/SecuritiesFirm/SecuritiesAccount와 동일한 명명 스타일).

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

---

## 2026-09-21 — SF-1 완료 (Ipo 엔티티/Repository/마이그레이션)

### 구현 내용
- **패키지 구조**: `com.ipocalc.ipocatalog` 루트 패키지 사용
  - `domain/model/ipo/Ipo.java` — JPA 엔티티 (id Long IDENTITY, companyName/ipoName 필수, stockCode/roundLabel/lowerBandPrice/upperBandPrice/fixedPrice/listingDate/offeringShareCount 선택, subscriptionStartDate/subscriptionEndDate 필수, status 기본값 ACTIVE, deletedAt/createdAt/updatedAt)
  - `repository/ipo/IpoRepository.java` — `findByIdAndDeletedAtIsNull`, `findAllByDeletedAtIsNullOrderByCreatedAtDesc` 메서드 포함
- **Flyway 마이그레이션**: `V5__create_ipos_table.sql` (기존 V4 다음 버전)
  - PostgreSQL 전용 문법 (`GENERATED ALWAYS AS IDENTITY`)
  - partial unique index 2개: `uk_ipo_stock_code_active` (stock_code IS NOT NULL AND status='ACTIVE'), `uk_ipo_name_start_date_active` (status='ACTIVE')
  - 조회 최적화 인덱스: `idx_ipo_status`
- **테스트**: `IpoRepositoryTest.java` 7개 테스트 모두 통과
  - 기본 저장/조회, deletedAt 필터링, partial unique index 제약 검증 (stock_code 중복, ipo_name+start_date 중복, stock_code NULL 허용, 논리 삭제 후 재등록 허용)

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 **147개 통과** (신규 7개 포함, 기존 테스트 영향 없음)
- 마이그레이션 적용 및 엔티티 매핑 정상 확인

## 2026-09-21 — SF-2 완료 (IPO 등록 API)

### 구현 내용
- `controller/ipo/IpoController.java` — `POST /ipos`
- `service/ipo/IpoService.java` — 필수값/날짜순서/가격범위/255자/offeringShareCount 검증, `checkDuplicate()` 중복검사 헬퍼(D-IPO-001, stockCode 유무 분기, 다음 SF에서 재사용 예정)
- `controller/ipo/dto/{IpoRegisterRequest, IpoResponse}.java`, `exception/{IpoValidationException, IpoConflictException, IpoExceptionHandler}.java`
- 워커(OpenRouter 무료 모델)가 구현/테스트 작성까지는 진행했으나, 마지막 테스트 실패를 스스로 해결하지 못하고 도구 호출 없이 멈춤(stall) — Master가 직접 진단·수정

### Master가 발견·수정한 버그: V5 마이그레이션의 partial unique index 결함
- **증상**: spec AC-006(동일 종목명, 다른 stockCode/roundLabel이면 여러 회차 등록 성공) 테스트가 409로 실패.
- **원인**: `V5__create_ipos_table.sql`의 `uk_ipo_name_start_date_active` 인덱스가 `WHERE status = 'ACTIVE'`만 걸려 있어 stockCode 유무와 무관하게 (ipo_name, subscription_start_date) 조합 전체에 유니크를 강제함. Service 계층의 `checkDuplicate()`는 stockCode가 있으면 stockCode만 검사하고 name+date는 안 보는데, DB 제약은 이를 반영하지 못해 stockCode가 다른 정당한 회차 등록까지 `DataIntegrityViolationException`으로 막아버림. 이는 architecture.md 자체가 미리 경고했던 리스크("부분 유니크 인덱스와 Service 계층 검증의 동작이 어긋나는 경우 구현 단계 TC로 검증")가 실제로 발생한 사례.
- **수정**: `V6__fix_ipo_name_start_date_unique_index.sql` 추가 — 인덱스 조건에 `AND stock_code IS NULL`을 더해 Service 계층 규칙과 정확히 일치시킴.

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 **160개 통과** (신규 13개 포함, 실패/에러 0)

## 2026-09-21 — SF-3 완료 (IPO 목록/상세 조회 API)

### 구현 내용
- `controller/ipo/IpoController.java` — `GET /ipos`, `GET /ipos/{id}`
- `service/ipo/IpoService.java` — `list()`, `getById()` 메서드
- `exception/IpoNotFoundException.java` — REST 컨트롤러용 NotFoundException
- `exception/IpoExceptionHandler.java` — `IpoNotFoundException` 핸들러 추가 (404 + NOT_FOUND)

### 테스트 (Test-First: RED → GREEN)
`IpoControllerTest`에 6개 신규 테스트 추가 (전체 19개):
1. `listIposWithExistingIposReturns200AndListInDescendingOrder` — 목록 조회 정상, createdAt 내림차순 확인
2. `listIposExcludesDeletedIpos` — 삭제된 IPO 제외 확인
3. `listIposWithNoIposReturns200AndEmptyArray` — 빈 목록 200 + 빈 배열
4. `getIpoByIdExistingReturns200AndIpo` — 상세 조회 정상
5. `getIpoByIdNotFoundReturns404` — 존재하지 않는 ID → 404
6. `getIpoByIdDeletedReturns404` — 논리 삭제된 ID → 404

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 **166개 통과** (신규 6개 포함, 기존 테스트 영향 없음, 실패/에러 0)

## 2026-09-21 — SF-4 완료 (PATCH /ipos/{id} 수정)

### 구현 내용
- `controller/ipo/dto/IpoUpdateRequest.java` — 모든 필드 nullable
- `controller/ipo/IpoController.java` — `@PatchMapping("/{id}")`
- `service/ipo/IpoService.java` — `update(Long id, IpoUpdateRequest)` (부분 수정, 유효성/중복 검사, 자기 자신 제외, updatedAt 갱신)
- `IpoControllerTest.java` — 8개 신규 PATCH 테스트 추가 (정상 수정, 404, 400 날짜/가격/255자, 409 중복, 자기 값 재수정 허용)

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 통과 (기존 + 8개 추가, 실패 0)

## 2026-09-21 — SF-5 완료 (DELETE /ipos/{id} 비활성화)

### 구현 내용
- `controller/ipo/IpoController.java` — `@DeleteMapping("/{id}")` 추가, `deactivate(Long id)` 메서드 호출 후 204 No Content 반환
- `service/ipo/IpoService.java` — `deactivate(Long id)` 메서드 구현
  - `findByIdAndDeletedAtIsNull(id)`로 조회, 없으면 `IpoNotFoundException(404)`
  - `status`를 `INACTIVE`, `deletedAt`과 `updatedAt`을 현재 시각으로 설정
  - `IpoRepository.save()`로 저장
- `IpoControllerTest.java` — 3개 신규 DELETE 테스트 추가
  1. `deactivateIpoReturns204AndStatusInactiveAndDeletedAtSet` — 정상 비활성화(204, DB에서 status=INACTIVE + deletedAt 설정 확인)
  2. `deactivateIpoWithNotFoundIdReturns404` — 존재하지 않는 ID(404)
  3. `deactivateAlreadyDeletedIpoReturns404` — 이미 비활성화된 ID 재시도(404, 기존 프로젝트 패턴 따름)

### 설계 결정
- 물리 삭제가 아닌 논리 삭제: `status=INACTIVE` + `deletedAt` 설정만 수행
- 연결된 Subscription이 있어도 항상 성공: FK `ON DELETE RESTRICT`는 물리 삭제만 제한하며, UPDATE로 status 변경은 무관
- 소유권 검증 불필요: Ipo는 글로벌 기준정보
- 이미 삭제된 ID 재시도 → 404: `findByIdAndDeletedAtIsNull` 사용 시 이미 삭제된 건 자연히 조회되지 않으므로 404

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 **177개 통과** (신규 3개 포함, 실패/에러 0)

## 2026-09-21 — SF-6 완료 (POST /ipos/import CSV 일괄 등록) + Epic 5 전체 완료

### 구현 내용 (Test-First: RED → GREEN)
- **build.gradle.kts** — `org.apache.commons:commons-csv:1.14.1` 의존성 추가 (D-IPO-003: Maven Central 최신 안정 버전 확인 후 지정, 일부러 직접 split(",") 구현하지 않음)
- **DTO 신규** — `controller/ipo/dto/IpoImportResponse.java` (successCount/failureCount/failures), `IpoImportFailure.java` (rowNumber/reason)
- **예외 신규** — `IpoInvalidCsvException`(→400 INVALID_CSV), `IpoFileTooLargeException`(→400 FILE_TOO_LARGE), `IpoExceptionHandler`에 핸들러 추가, 컨테이너 레벨 `MaxUploadSizeExceededException`(5MB 초과)도 FILE_TOO_LARGE로 매핑
- **`service/ipo/IpoCsvImportService.java` 신규** (이 메서드 자체는 비트랜잭션)
  - commons-csv(RFC 4180 — 콤마/큰따옴표 특수문자 안전 파싱)로 UTF-8 읽기, 헤더 레코드에서 컬럼 인덱스 맵 구성
  - 필수 헤더(companyName, ipoName, subscriptionStartDate, subscriptionEndDate) 누락 시 400 INVALID_CSV
  - 빈 파일 400 INVALID_CSV, 5MB 초과 400 FILE_TOO_LARGE (서비스 가드 + `spring.servlet.multipart.max-file-size: 5MB` 이중 방어)
  - 행별로 `IpoService.register()` 호출 — **SF-2 기존 검증/중복검사 로직(private 헬퍼 포함)을 그대로 재사용**, 신규 검증 로직 없음
  - 각 행이 개별 트랜잭션으로 저장(파일 전체 롤백 없음): 행 실패 시 `IpoValidationException`/`IpoConflictException`/파싱 오류를 catch해 failures에 (rowNumber, reason) 추가 후 다음 행 계속
  - 날짜 비-ISO8601 또는 숫자 필드 비숫자 값 → 해당 행만 실패
- **`controller/ipo/IpoController.java`** — `@PostMapping(value="/import", consumes=MULTIPART_FORM_DATA_VALUE)`, `@RequestParam("file")` 추가, **한글 클래스 주석 신설**(AGENTS 15장): "IPO(공모주) 기준정보에 대한 등록/조회/수정/비활성화/CSV 일괄등록 API를 제공한다."

### 테스트 (Test-First: RED → GREEN)
`IpoCsvImportTest` 신규 7개 (전체 184개):
1. `importCsvWithAllValidRowsReturns200AndSuccessCount` — 전체 성공 (AC-018)
2. `importCsvWithSomeInvalidRowsReturns200AndFailureDetailsWithoutRollback` — 부분 성공: 날짜형식 오류(행3)/중복(행5)/필수값 누락(행6) 각각 실패, 유효행은 저장 (AC-019)
3. `importCsvWithNonNumericPriceMarksOnlyThatRowFailed` — 숫자 필드에 비숫자 값 행만 실패
4. `importCsvWithSpecialCharactersParsesSafely` — 큰따옴표 안 콤마/이스케이프 따옴표 → 정확히 저장 (D-IPO-003 검증)
5. `importCsvWithEmptyFileReturns400InvalidCsv` — 빈 파일 400 INVALID_CSV (AC-020)
6. `importCsvWithMissingRequiredHeaderReturns400InvalidCsv` — 필수 헤더 누락 400 INVALID_CSV (AC-020)
7. `importCsvWithTooLargeFileReturns400FileTooLarge` — 5MB 초과 400 FILE_TOO_LARGE (D-IPO-002)

### 테스트 실행 결과
```bash
DB_PASSWORD=ipo_local_password ./gradlew test --rerun
```
- 전체 테스트 스위트 **184개 통과** (신규 7개 포함, 실패/에러 0)
- RED 단계에서 신규 7개 전원 실패 확인 후 GREEN 전환

### Epic 5 (ipo-catalog) 전체 완료
SF-1~SF-6이 모두 GREEN으로 마무리되어 Epic 5 전체 완료. 커밋/푸시/PR은 Master가 직접 검증 후 처리.

- 2026-10-14 사용자 결정으로 공모주 CSV 가져오기 제거(공모주는 관리자 수동 등록만).
