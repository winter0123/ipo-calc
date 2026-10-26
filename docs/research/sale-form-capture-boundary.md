# 매도 등록 캡처 적용 경계 조사

`useSaleForm`은 투자자/청약 조회, 선택·기본값 초기화, RHF 입력, 수익 연동 계산, 캡처 입력 대기/적용, 제출을 함께 조정한다. OCR 자체는 이미 `useCaptureImport`에 있고 계산식은 `entities/sale/saleMath.ts`에 있지만, 캡처 결과를 기다렸다가 필드에 적용하고 표시 마크/안내를 기록하는 effect와 상태는 폼 훅에 결합돼 있었다.

캡처 적용은 독립 입력 흐름으로 분리할 수 있다. 적용에 필요한 query 결과, RHF setter/getter, 계산 연동 함수, 청약 선택 callback만 인자로 전달한다. 투자자/쿼리/기본값/제출 책임 및 화면 공개 `SaleForm` shape는 그대로 둔다.
