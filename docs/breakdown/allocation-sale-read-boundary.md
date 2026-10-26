# Allocation·Sale GET 경계 Breakdown

## SF-1 — 상세 GET schema 및 cancellation 적용

- 수정: `ui/src/entities/{allocation,sale,subscription}/types.ts`, `api-client.ts`, 각 api-client 테스트, 세 편집 dialog queryFn.
- AC: spec의 DTO 검증, signal 전달, 편집 동작 유지 조건.
- 검증: SF 묶음 완료 후 UI test/lint/typecheck/format:check/build를 마지막 종합 검증에서 실행한다.
- 제외: mutation과 API 계약 변경.
