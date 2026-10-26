# 매도 폼 캡처 적용 경계 작업 기록

## 2026-10-26 — 구현

- `useSaleForm`에서 캡처 대기 상태, 준비 데이터 이후 적용 effect, captured field mark 및 notes 처리를 `useCaptureFill`로 이동했다.
- 화면에서 사용하는 `SaleForm`의 반환 구조와 기존 청약 선택/초기화/손익 계산/제출 경로를 유지했다. 새 hook과 타입 사이 순환 import는 만들지 않았다.
- 캡처의 수량·가격·날짜·실현손익 적용, 손익 기준 비용 역산 및 불일치 안내를 확인하는 통합 hook 테스트를 추가했다.
- 전체 UI 테스트·lint·typecheck·format·build는 누적 refactoring 종료 시 한 번 실행한다: `NOT_RUN`.
