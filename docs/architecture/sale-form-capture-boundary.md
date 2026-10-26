# 매도 폼 캡처 적용 경계 구조

```text
useCaptureImport (OCR/record matching)
  → SaleForm.applyCapture
  → useCaptureFill (deferred apply, captured fields, notes)
  → RHF fields + existing syncLinked
```

`useSaleForm`은 현재 투자자/청약 선택, query, 기본값, 제출 및 공개 form model을 소유한다. `useCaptureFill`은 비동기 데이터 준비를 기다리는 캡처 적용 흐름만 소유한다. 두 hook은 `saleFormTypes.ts`의 타입과 명시적 callback으로 연결해 순환 import를 만들지 않는다.
