# Allocation·Sale GET 경계 구조

```text
Edit dialog useQuery({ queryFn: ({ signal }) => getRecord(id, signal) })
  → entity api-client GET
  → apiGetParsed(path, responseSchema, signal)
  → apiFetch(path, { method: 'GET', signal })
```

응답 schema는 backend `AllocationResponse`/`SaleResponse`/`SubscriptionResponse`의 필드와 nullability를 반영한다. API shape를 좁혀 추정하지 않는다. 저장 mutation과 캐시 invalidation은 기존 코드를 유지한다.
