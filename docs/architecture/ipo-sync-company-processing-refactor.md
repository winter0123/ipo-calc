# IPO 회사별 동기화 처리 아키텍처

## 처리 경로

```text
sync(daysBack) ── DS001 recent ──┐
                                 ├─ company loop → shared processCompany(fetchDetails)
syncByMonthRange ─ DS001 month ──┘                  ├─ stock_code filter
                                                    ├─ DS006 fetch (caller-specific)
                                                    ├─ result/error classification
                                                    ├─ latest filing + date validation
                                                    └─ per-company transaction → existing upsert
```

각 상위 흐름은 호출 방식에 맞는 `Function<String, List<OpenDartEquitySecurityDetail>>`를 내부 회사 처리기에 전달한다. 회사 처리기는 어떤 API/기간으로 상세를 가져오는지 알지 않고, fetch 예외의 기존 의미와 도메인 처리를 공통으로 수행한다.

결과 DTO의 초기화는 별도 동기화 경로가 아니라 `emptyResult()` factory 한곳에서 수행한다. 각 호출마다 새로운 `failures` 목록을 만든다.

## 불변식

- run lock과 일정 병합 시점은 상위 coordinator가 소유한다.
- DS001 인증/020/일반 API 오류는 실행 중단 정책을 유지한다.
- DS006 인증/020은 실행 중단, 기타 API 오류는 회사 실패 후 계속한다.
- 각 IPO 저장은 별도 transaction 안에서 실행한다.

## 영속화 경계

`IpoSyncService`는 외부 조회·회사별 오류 분류·건별 `TransactionTemplate` 경계를 소유한다. transaction callback 내부에서만 `IpoSyncPersistence`를 호출해 저장소 조회와 수동/자동 IPO 판별, 생성·갱신을 수행한다. persistence outcome으로 coordinator가 created/updated/skippedManual 카운터를 갱신한다.
