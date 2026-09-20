# 작업 로그: 대시보드 집계 API (dashboard-aggregation-api, Epic 12)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 스코프 정정)

`docs/research/dashboard-aggregation-api.md`는 Epic 10(profit-calculation)/
Epic 11(investment-query-api)의 조사 문서를 인용했는데, 그 조사 문서들의
**불확실했던 초기 결론**을 그대로 인용했을 뿐 이후 Master가 확정한
D-P-001~006 / D-Q-001~006 결정을 반영하지 못했다. 이를 Epic 12에
맞게 정렬한다.

### D-D-001: 총투자금 정의는 Epic 10 D-P-005와 동일 (배정금액 단일 기준)
조사 문서는 "사용자 선택 옵션 제공 필요"라고 했지만, 이미 Epic 10에서
**배정금액(Allocation.allocatedQuantity × allocationPrice) 단일 기준**으로
확정했다([[profit-calculation]] D-P-005). 대시보드도 동일 기준을 그대로
사용하며 별도 선택 UI를 만들지 않는다.

### D-D-002: 집계는 저장하지 않는 파생값, 별도 이력/메타데이터 인프라 불필요
조사 문서가 언급한 "출처 메타데이터", "정책 버전/일자" 저장은 Epic 10에서
이미 기각한 CalculationTrace 설계([[profit-calculation]] D-P-001)와 같은
개념이다. 대시보드 집계도 동일하게 **on-demand 계산, 저장하지 않음**
원칙을 따른다.

### D-D-003: 안분 잔차는 재분배하지 않고 각 매도 건 값을 그대로 합산
"마지막 매도 건에 흡수" 같은 잔차 재분배 정책은 도입하지 않는다
([[profit-calculation]] D-P-003과 동일). 각 Sale의 독립 계산값(반올림
적용됨)을 단순 합산한 값이 곧 집계 결과다 — 수학적으로 group-by SUM은
항상 개별 항목의 합과 일치하므로 별도의 "일관성 검증" 로직/배치잡도
불필요하다(조사 문서가 제안한 검증 절차 5개 항목 중 4개는 SQL SUM의
수학적 성질로 자동 보장되므로 설계하지 않는다).

### D-D-004: 미매도 주식(미실현손익)은 이 Epic 범위에서 제외
로드맵이 명시한 제외 범위("미확정 평가손익의 실시간 표시")를 그대로
따른다. 대시보드는 **실현수익(Sale 기준)만** 집계하며, 미실현손익 지표는
설계하지 않는다. 보유 현황(잔여수량)이 필요하면 이미 Epic 11의 조회
API(`GET /investment-history`)가 `remainingQuantity`를 제공하므로
중복 설계하지 않는다.

### D-D-005: 기간별 귀속은 Sale.saleDate 기준
수익은 매도 시점에 실현되므로 월별/연도별 집계는 `Sale.saleDate`의
연-월/연 단위로 그룹화한다.

### D-D-006: 집계 계산은 Epic 10의 계산 로직을 그대로 재사용
새로운 수익 계산 공식을 만들지 않는다. Epic 10의 `ProfitCalculator`
로직(매도 건별 세전/세후 순수익 계산)을 그대로 호출한 뒤, 투자자별/
증권사별/기간별로 그룹화해 합산한다.

### D-D-007: 계층 집계 범위 — 전체/투자자별/증권사별/월별/연도별 5종
조사 문서가 언급한 "계좌별", "공모주별" 집계는 로드맵 원문(질문 4)에
명시되지 않은 확장이므로 MVP 범위에서 제외한다. 전체/투자자별/증권사별/
월별/연도별 5종의 집계만 제공한다.

이 결정들은 `docs/spec/dashboard-aggregation-api.md` /
`docs/architecture/dashboard-aggregation-api.md` 작성 태스크 지시에
반영되었다.
