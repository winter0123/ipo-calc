# 청약·매도 통합 관리 화면 architecture (unified-investment-screen)

> 현 코드 우선. 기존 DB/API/계산/암호화/캡처를 재사용하고, 새 금융 정책·통합 테이블·탭 분리를 만들지 않는다. 2026-11-12 Master 보정.

## 데이터 흐름

```
/ipos/:ipoId/manage (pages/TradeManagePage — 투자자/IPO/계좌/조회 문맥을 페이지가 단일 소유)
  ├─ useSecuritiesAccounts(investorId)            → 계좌 select (1개 자동선택)
  ├─ useIpo(ipoId) + useIpoInsight(ipoId)         → 고정 헤더/주관사 계좌 필터
  ├─ useInvestmentHistory({investorId, ipoId, securitiesAccountId, size:1})
  │    → 연결된 청약 id 탐색. 서버 이력 조회는 deletedAt IS NULL만 반환(구독 cancel 시
  │      deletedAt 설정)하므로 현재 데이터에서 활성 누락 원인이 아니다. 조합 (investor,
  │      ipo, account)는 ACTIVE 부분 유니크(V7)라 정확 필터 size:1이면 충분하다.
  │      응답의 subscriptionStatus === 'ACTIVE'를 검증하고, 비활성 유입 발견 시에만
  │      전건 검토로 전환한다.
  ├─ useInvestmentHistoryDetail(subscriptionId)
  │    → 잔여(remainingQuantity)·sales[]·상태·배정 요약(표시 전용).
  │      InvestmentHistoryItem에는 depositAmount/memo가 없고(types.ts:51-66),
  │      saleSummary에는 price가 없다(types.ts:40-49) — 표시 요약으로만 쓴다.
  ├─ 편집 원본: entities의 getSubscription(id)·getAllocation(id)·getSale(id)
  │    (GET /subscriptions/{id} · /allocations/{id} · /sales/{id})
  │    → quantity·depositAmount·subscribedAt·memo / allocatedQuantity·allocatedAmount·note /
  │      quantity·price(nullable)·amount·saleDate·fee·tax·otherCost.
  │      매도 단가는 amount÷quantity 추정이 아니라 원본 price(saleSchema, sale/types.ts)를 쓴다.
  │      price가 null이면 단가 미입력으로 원본 금액을 보존한다. PATCH는 변경 필드만
  │      포함(서버 null=무변경, Sale.updateDetails) — null·0을 무심코 덮어쓰지 않는다.
  ├─ 청약 섹션  → 기록 없으면 POST /subscriptions, 있으면 PATCH /subscriptions/{id}
  ├─ 배정 섹션  → 기록 없으면 POST /allocations, 있으면 PATCH /allocations/{id}
  ├─ 매도 섹션  → "+매도 추가" → POST /sales(저장 후 상세 재조회 완료 전 다음 추가 잠금),
  │             행별 수정 PATCH /sales/{id}, 삭제(논리 취소) DELETE /sales/{id} — 모두 현재 페이지
  └─ 저장 성공 시 investmentHistoryKeys + profitKeys + dashboardKeys invalidate (useRecordMutation 관례)
```

## FSD 모듈 경계 (선택지 확정)

- 조합은 **pages 레이어**, 상태는 **pages가 단일 소유**: TradeManagePage가 투자자/IPO/계좌/조회 문맥을 소유하고, features의 폼에는 **문맥(공모주·투자자·계좌·기존 기록)과 onSaved 콜백/편집 모드를 props로 주입**한다.
- features 폼에서 ① 자체 `useNavigate` 페이지 이동 제거(등록 성공 후 **같은 페이지 유지**), ② 자체 계좌 select 중복 금지(페이지 select만 사용), ③ 라우트 URL query(`useSearchParams`)를 다시 읽는 강결합을 그대로 복사하지 않는다.
- 편집 Dialog 재사용 = 단가/수량 검증(MoneyInput validate, AllocationEditDialog의 정수·0 이상 규칙) 등 **기존 검증 로직을 추출/참고**해 섹션에서 재사용. 새 중복 폼 대량 복사 금지.
- features 간 직접 import 금지 유지. 공유는 entities 공개 API와 query key로만.
- 정확한 파일 소유권은 breakdown SF에서 확정.

## API·DB 영향

- **변경 불필요(근거)**: 편집 원본 GET 3종(`/subscriptions/{id}`·`/allocations/{id}`·`/sales/{id}` — UI `getSubscription`/`getAllocation`/`getSale` 구현됨), 이력 GET 2종(`/investment-history`·`/{id}` — remainingQuantity·sales), 쓰기 POST/PATCH/DELETE 모두 기존 컨트롤러/서비스에 있다. 연결 탐색은 기존 정확 필터(`investorId`+`ipoId`+`securitiesAccountId`)+size:1+`subscriptionStatus === 'ACTIVE'` 검증으로 충분하므로 API/DB 변경이 필요 없다.
- 복합 API는 불필요: 부분 실패는 서버 409(중복)/400(상한)으로 막히고 섹션 재조회로 복원(`uk_subscription_active_combo`).
- DB: 스키마·Flyway 변경 없음. V7/V8/V9 식별 단위(투자자×IPO×실계좌 / 청약 1:1 배정 / 배정 1:N 매도) 유지.

## 금액·수수료 규칙 (현 코드 사실)

- `fee` = 공모주수수료, `tax` = "세금 및 기타". `toSalePayload`: price×quantity=`amount`, `fee=ipoFee`, `tax=otherFees`(saleMath.ts:145-153). `otherCost` 컬럼은 payload에 보내지 않는다.
- 실현손익 = 매도금액 − 취득금액 − 세금및기타(`realizedProfitFromFees`), 최종수익금 = 실현손익 − 공모주수수료(`finalProfit`). 잔여 = 배정 − ΣACTIVE 매도(백엔드 `calculateRemainingQuantity`).
- 공모주수수료 기본 2,000원, 배정에 ACTIVE 매도가 이미 있으면 기본 0원(`useSaleForm.ts:397-398`) 보존. 실제 수수료는 사용자가 수정 가능하고 서버는 하드과금하지 않는다.
- 매도 단가·금액은 원본 값 유지. amount÷quantity 재계산으로 표시를 덮어쓰지 않는다.

## 암호화·자격증명·캡처

- 계좌번호 복호화/마스킹/브로커 자격증명은 백엔드 불변 — UI에서 평문을 다루지 않는다.
- 캡처 자동입력(`CaptureImport`/`useCaptureFill`/`useCaptureImport`)은 매도 섹션에 재사용. 다른 계좌로의 자동 전환 금지(2026-11-11 보완) 유지.
- 계좌 select 옵션은 기존 `useSecuritiesAccounts`로 번호 없는 계좌도 포함해 표시(계좌명 우선, V42로 번호 nullable).

## 리스크

- 계좌 전환 시 미저장 dirty 상태 → 전환 전 인라인 경고(취소=유지, 확인=새 문맥 재조회+입력 초기화). dirty 금액이 다른 계좌로 새지 않게 한다.
- 매도 추가 후 상세 재조회 완료 전 다음 추가 잠금. 응답 유실(서버 성공/클라이언트 실패)은 자동 POST 재전송 금지 → 재조회해 목록/잔여를 보여 주고, 동일 일자·수량·단가의 정상 분할 매도와 구분할 수 없어 어떤 요청이 성공했는지 100% 식별 불가함을 사용자에게 명시한다. 사용자가 목록을 보고 직접 결정한다. 영구 중복 방지(idempotency 키)는 현 API로 미보장 — 이 계획의 필수 요건으로 넣지 않는다.
- 사후 등록 자동 생성 청약은 실제 청약 사실이 아니다(quantity=배정수량, depositAmount=0, subscribedAt=생성 시각, memo='매도/배정 등록에서 자동 생성된 청약 연결 기록' — AllocationService.java:79-91). 청약기간 밖일 수 있으며, 화면은 memo 마커로 안내하고 사용자가 나중에 실제 값으로 수정할 수 있게 한다. 기간 외 기존 subscribedAt은 보존(강제 변경 금지), 인라인 dirty 입력은 덮어쓰지 않는다. 사용자 데이터 변경/일괄 보정 없음.
- 이중 클릭 잠금은 버튼 isPending+서버 400/409로만. 동일 날짜·단가의 정상 분할 매도를 막는 unique 제약 금지.
- 0 배정(allocatedQuantity=0): 잔여=0이라 매도 추가 불가, 기존 건 수정/삭제는 유지. 0 잔여면 추가 불가.
- 섹션 저장 순서: 청약 저장 전 배정/매도의 직접 저장은 연결이 없어 불가 — 단 매도/배정부터 사후 등록하면 서버가 (투자자, IPO, 계좌)로 기존 ACTIVE 청약을 찾거나 청약·배정을 자동 생성하므로 가능하다(사후 등록 자동 연결 예외, AC-2).
- 삭제는 현 논리 취소(status=CANCELLED+deletedAt) 유지, 자식→부모 순서(매도→배정→청약). UI는 서버 HAS_CHILD_RECORDS 메시지를 그대로 표시.
- FSD 경계 위반(feature→feature 직접 import) — 조합은 반드시 pages.
- 기존 미커밋 계좌관리 변경과 같은 worktree — 구현 SF는 계좌관리 파일을 수정하지 않고 단일 트리 순차 진행.
