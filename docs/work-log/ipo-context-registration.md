# work-log: ipo-context-registration (공모주별 등록 진입)

- 일시: 2026-11-11 · 브랜치: winter0123/remove-allocation-method · 담당: frontend 워커(depth 1)

## 변경 파일
- SubscriptionListPage: 상단 공통 등록 버튼 삭제(카드/상세 링크 유지), 미사용 styles 제거, 테스트 교체
- pages/SubscriptionRegisterPage: 폼 key에 ipoId 포함(투자자·공모주 전환 시 낡은 값 잔류 방지)
- features/subscription-register/SubscriptionRegisterForm:
  - 공모주 select 완전 제거 → URL `?ipoId=` 단건 조회(useIpo). 이름은 고정 텍스트(변경 불가), 공모가·청약 기간 표시
  - 문맥 없음 = '공모주를 선택해 들어와 주세요' 안내+`/subscriptions` 링크, id 형식 오류 = 오류 alert 안내, 조회 실패/없음 = 오류 alert, 모두 폼 미렌더+임의 fallback 없음
  - 청약일 `input[type=date]` 제거 → subscriptionStartDate~EndDate 실제 날짜 버튼(type=button, aria-pressed, 키보드 조작 가능). 기간 전체(같은 날 1개, 초과 일수 포함) 선택 가능
  - 일정 누락/형식 오류/역전(start>end) → '청약 일정을 확인할 수 없어 등록할 수 없습니다' alert + 등록 버튼 disabled(임의 오늘값·달력 fallback 없음)
  - 기본값: 한국 오늘(Asia/Seoul, Intl 'sv-SE')이 기간 내면 오늘, 아니면 첫날 / payload `${subscribedAt}T09:00` 유지 / 기간 밖 날짜는 validate·onSubmit 이중 차단
  - 배정/증거금/주관사 계좌 자동연결 로직 보존
- features/subscription-register/subscriptionForm.ts: subscriptionPeriodDates/defaultSubscribedAt/koreanToday 헬퍼 추가
- features/sale-register/useSaleForm.ts + SaleRegisterForm.test.tsx: `?ipoId=abc`/`?subscriptionId=abc` 형식 오류를 guide가 아닌 오류 안내(entryProblem)로 분기. 기존 subscriptionId→detail→ipoId 고정 표시 보존
- widgets/EmptyState, features/profit-view/ProfitTable(+테스트): 문맥 없는 등록 링크 → `/subscriptions` 공모주 청약 목록 링크
- pages/ProfitViewPage.test.tsx: 링크 기대값 갱신
- 문맥 있는 등록 링크(IpoDetail·ScheduleList·Card·Dashboard·InvestmentHistory·allocations)는 rg로 확인 후 유지
- docs/user-guides/manual/mobile.html·pc.html: 청약 등록하기 절(공모주 고정·날짜 버튼·기본값·안내 화면), 청약 목록 절, ProfitTable 빈 상태 링크 문단 갱신
- docs/breakdown/ipo-context-registration.md 신규

## 검증 (실제 실행)

- `pnpm vitest run`: 108파일 945개 PASS (typecheck 재수정 1건: 미사용 Link import 제거)
- `pnpm typecheck`: PASS · `pnpm lint`: 0 error(기존 warning 4건, 타 작업자 파일) · `pnpm build`: PASS
- `npx prettier --check`(변경 ui 파일): 전부 PASS
- 가이드 HTML 2개는 prettier FAIL(HEAD에서도 exit 1, 사전 상태); 내 편집은 파일 기존 스타일 유지
- `git diff --check`: PASS

## 발견
- 배포/서버 검증 계약 충돌 없음: SubscriptionService.register는 subscribedAt 필수·LocalDateTime만 검증(기간 검증 없음) → 클라이언트가 기간 밖 날짜를 보내지 않는 것으로 충분. 과거 청약 사후 등록도 서버 차단 없음
- `allocationId=` 진입 URL은 저장소에 존재하지 않음(rg 0건) — 호환 대상은 `subscriptionId`뿐. subscriptionId는 매도 폼에서 detail→ipoId 확인 후 고정 표시됨(기존 로직 보존)
- 사용자 가이드에 "메뉴 새로 기록" 메뉴는 코드에 존재하지 않음(rg 0건). 배정 입력 절 등 가이드 전반의 메뉴 서술은 본 작업 범위 밖 사전 드리프트 — 보고만
- 가이드 HTML 2개는 다른 워커의 미커밋 변경 파일과 공유 — 편집은 청약/매도 등록 문단만

## 남은 사항
- 배포 없음(범위 외). 화면 실동작 확인은 배포 후
- 가이드 "새로 기록" 메뉴 드리프트는 docs 작업으로 분리 권장
