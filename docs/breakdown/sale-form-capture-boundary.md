# 매도 폼 캡처 적용 경계 Breakdown

## SF-1 — 캡처 적용 hook 추출

- 대상: `useSaleForm`, 신규 `useCaptureFill`, sale form types/utils.
- 완료: pending/apply 상태와 필드 적용 effect 이동, 기존 `SaleForm` 공개 shape와 청약 선택 초기화 유지.
- 테스트: 손익 기준 역산, 캡처 표시 필드 및 notes를 기존 `useSaleForm.test.tsx`로 검증.
- 검증: 누적 UI refactor 종료 시 UI 전체 test/lint/typecheck/format/build 실행.
