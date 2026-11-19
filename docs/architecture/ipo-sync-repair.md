# IPO-Sync-Repair 아키텍처 (Phase 0)

> **Epic**: E17 후속 결함 수선 (GitHub #507)
> **범위**: Phase 0 설계. 소스 수정 없음 / **상태**: 초안

## 1. 데이터 플로우

```
DS001 목록(receivedOn→rceptNo 역순 max) → DS006 상세(rcept_no 그룹) → 원문 ZIP 파서(정정전/정정후 구분)
  → Ipo.applyVerifiedOfferingTerms → DB (밴드·확정가, 항목별 sourceReceiptNo)
ustockplus /v2/ipo/calendars (today-30/+59, 89일) → IpoScheduleMergeService.merge
  → Ipo.mergeSupplementalSchedule (수동 스킵·추적값 보호)
```

## 2. 창 계산

- 복원: `today.minusDays(30)` / `today.plusDays(59)` (차이 89일, 로컬 가드 통과).
- 폐기: 청약시작일 기준 ±90일 (최대 180일) — 89일 가드 위반으로 동기화 조용히 중단의 원인.
- 89일 가드는 로컬 검증 사실. uStockPlus API 자체의 외부 제한은 확인 전 → 창 축소 필요 시 재평가.

## 3. 보호 규칙 (기존 재사용)

- 수동 레코드(`syncSource==null`) 스킵.
- `canAutoOverwrite`: 현재 null이거나 `last_imported_*`와 같을 때만 자동 정정 → 후퇴 방지.
- `source_receipt_no`/`withdrawn_receipt_no`로 원공시 매칭, `uk_ipo_corp_code_source_receipt_active`로 다른 접수번호 재추진 허용·같은 공모 중복 차단.
- 건별 독립 트랜잭션, 예외 격리(한 건 실패가 다음 건을 막지 않음).

## 4. 스키마

V44 컬럼 재사용 — Phase 0에서는 새 마이그레이션 없음. `band_price_source_receipt_no`, `fixed_price_source_receipt_no`, 파서 버전 컬럼을 원문/요약 출처 분리에 활용.

## 5. 미확인·위험

- 외부 89일 제한 미확인, `finalOfferPrice` 의미 미확인(슬롯 표현만 유지), DS006 `slprc` 단위 미확인, 영광와이케이엠씨 접수번호 대응 미확인.
- 운영 반영은 미리보기 + 별도 승인 후. IPO ID 보존, 정정/철회/재추진 시 일정 보호.

## 6. 검증 포인트

`UstockPlusCalendarClientTest`, `IpoScheduleMergeServiceTest`, `IpoReconciliationServiceTest`, `AutomaticReconciliationTest` 회귀.
