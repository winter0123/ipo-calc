# 매도 폼 캡처 적용 경계 Spec

## Acceptance Criteria

1. 캡처 대기·필드 적용·captured 표시 및 불일치 notes 상태를 `useCaptureFill` hook이 소유한다.
2. 캡처 적용이 다른 청약을 지정하면 선택 callback을 통해 기존처럼 폼 값을 초기화하고, 데이터가 준비된 뒤 값을 적용한다.
3. realized profit이 캡처에 있으면 이를 우선해 수수료/기타 비용을 역산하고 불일치 안내를 보존한다.
4. 일반 청약 선택, 자동 기본값, 손익 연동, submit 동작 및 기존 `SaleForm` 외부 타입은 유지한다.

## 비범위

OCR 인식/매칭 규칙, API payload, 투자내역 query 및 saleMath 공식 변경.
