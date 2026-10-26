# Backend `@Query` 제거 및 JPA/QueryDSL 전환

## 목표

Spring Data repository의 직접 `@Query` 문자열을 제거한다. 단순 조건·정렬·삭제는 Spring Data JPA 파생 메서드로 두고, 조인·동적 필터·집계·조건부 갱신은 QueryDSL 사용자 정의 repository로 옮긴다. 기존 트랜잭션, 권한 범위, 정렬, soft-delete, 동시성 및 중복 요청 의미를 보존한다.

## 전환 판단

| 유형 | 방식 | 대상 예시 |
|---|---|---|
| 고정 필드 조건·기간·정렬 | Spring Data JPA 파생 메서드 | 공지 활성 기간, IPO 상장가 수집 대상, 휴장일 날짜 범위 |
| 연도 추출·다중 연도 조건 | QueryDSL | `MarketHolidayRepository` |
| 소유권 조인·선택 필터·페이지 조회 | QueryDSL | Subscription, Allocation, Sale |
| SUM/COALESCE, 원자적 조건 갱신 | QueryDSL | Subscription/Allocation/Sale, Account 로그인 잠금, broker token |
| PostgreSQL `ON CONFLICT DO NOTHING` | DB 기능을 유지할 persistence adapter 검토 | Announcement dismissal; JPA QueryDSL의 upsert 지원 여부와 원자성 보존을 확인한 뒤 결정 |
| 도메인 경계 넘어 자격정보 물리 삭제 | 호출 방향을 역전시키지 않는 adapter 검토 | SecuritiesAccount/BrokerIntegration |

## 현재 진행 메모

- QueryDSL JPA Jakarta 의존성과 annotation processor 및 공용 `JPAQueryFactory` 설정을 추가했다. QueryDSL 5.1.0은 HQL injection advisory 대상이라 패치 버전이 공개된 OpenFeign QueryDSL 6.10.1로 선택했다.
- Announcement 활성 기간, IPO open-price 후보, MarketHoliday source count/date deletion은 Spring Data JPA 파생 메서드로 전환했다.
- MarketHoliday 연도·다중 연도·연도별 count, Subscription/Allocation/Sale 조인·집계·잠금, Account 로그인 상태 갱신, broker token 조건부 갱신을 QueryDSL fragment로 전환했다.
- 계좌 API 자격정보 삭제는 securities-account가 broker 도메인에 직접 의존하지 않도록 `AccountCredentialDeletion` port를 두고 broker persistence 구현체가 맡는다. 청약/배정 간 참조도 조회 port로 역전시켜 패키지 순환을 막는다.
- 공지 숨김은 계정 행 비관적 잠금 아래 복합 키 확인 후 JPA persist로 바꿔 동시 중복 요청을 직렬화한다. DB unique constraint는 최종 방어선으로 유지한다.
- 전체 `backend/src/main/java`에서 `@Query`와 `ACTIVE_HQL` 검색 결과는 0건이다.
- ArchUnit 순환 의존성은 두 도메인 간 조회 경계를 port/adapter로 바꿔 해소했다. Spring Data custom repository 구현체와 application port adapter도 분리해 중복 Bean 주입을 제거했다.
- 검증 완료: `DB_NAME=ipo_slot_4 ./gradlew test --rerun` 통과(1,101건, 실패 0), `checkstyleMain checkstyleTest spotlessCheck --rerun-tasks` 통과, `build` 통과.
- `git diff --check` 통과. `backend/src`의 `@Query` 및 `ACTIVE_HQL` 검색 결과는 0건이다.

## 완료 내역

저장소의 직접 선언 쿼리를 Spring Data JPA 파생 메서드 또는 QueryDSL custom repository로 전환했다. QueryDSL의 `Q` 엔티티 클래스는 annotation processor가 빌드 시 생성한다. API 계약 및 DB 스키마는 변경하지 않았다.

## 불변 조건

- API 응답·정렬·페이지 범위·권한 스코프는 바꾸지 않는다.
- soft-deleted 데이터는 기존대로 제외한다.
- 여러 계정·투자자 간 교차 조회가 발생하지 않는다.
- 로그인 잠금과 토큰 갱신은 원자적 갱신 조건을 유지한다.
- `AnnouncementDismissal` 중복 insert는 성공 처리로 유지한다.
- 기능 전환을 위한 DB schema/migration은 추가하지 않는다.
