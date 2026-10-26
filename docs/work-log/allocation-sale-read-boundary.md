# 투자 기록 상세 GET 검증 경계 작업 기록

## 2026-10-26 — 구현

- Allocation/Sale/Subscription 상세 GET response schema와 `z.infer` 타입을 추가했다.
- 해당 client가 `signal?: AbortSignal`을 받고 `apiGetParsed`를 사용하도록 변경했다. 각 edit dialog queryFn도 TanStack Query signal을 전달한다.
- 투자 기록·DTO·query key·mutation 계약은 바꾸지 않았다. nullable memo/가격/금액/삭제 시각을 response schema에 반영했다.
- API client schema mismatch 및 signal 전달 테스트를 추가했다.
- 사용자 요청대로 이 변경 뒤 테스트를 실행하지 않았다: `NOT_RUN`. 전체 변경 묶음 종료 시 종합 검증 1회 예정.

## 2026-10-26 — 누적 리팩토링 묶음 최종 검증

- UI 전체 90 test files / 709 tests, lint, typecheck, format:check, build PASS.
- 첫 전체 테스트 실행은 편집 테스트의 `apiFetch` mock이 `GET` 옵션을 쓰기 요청으로 오인해 4건 실패했다. mock 조건을 수정한 뒤 전체 테스트가 PASS했다.
- 상세 응답 Zod schema와 `AbortSignal` 전달 경로를 전체 UI suite 및 production build로 검증했다.
