# 매도 등록 자동 연결 (청약 선택 제거) 작업 로그

- 일자: 2026-11-11 / 브랜치: winter0123/remove-allocation-method / 역할: fullstack 워커(depth 1)
- 목적: 매도 등록에서 고객에게 보여 주던 '청약 선택'을 삭제하고, 고객은 공모주·투자자·증권계좌·매도정보만 입력하면 서버가 (투자자, 공모주, 증권계좌)로 청약·배정을 자동 연결한다. 배정방식 제거 작업이 같은 트리에서 먼저 끝난 뒤 순차 배정되어 이어붙였다.

## 확정 계약(코디네이터 최신 지시)
- 일반진입 IPO selector는 구현하지 않는다(기존 spec보다 우선). 청약·매도는 공모주별 버튼으로 진입하고 공모주명은 고정 표시, 공모주 select 금지.
- 매도 화면은 유효 ipoId 또는 소유한 subscriptionId/allocationId에서 해당 IPO를 확정해 고정 표시한다.
- 문맥 없는 /sales/register는 공모주 청약목록으로 가는 안내만 표시하고 임의 IPO 선택/등록을 하지 않는다.
- 공통 청약/매도 버튼 삭제·청약일 날짜버튼은 후속 task_050ccc69bce1 소관이라 건드리지 않았다(SubscriptionListPage 메뉴 버튼 등 기존 진입링크 라벨/위치 유지).

## 변경 파일
- UI: `features/sale-register/SaleRegisterForm/{useSaleForm,SaleRegisterForm,SaleConnectionFields,CaptureImport,useCaptureFill,useCaptureImport,saleFormTypes,SaleRegisterForm.styles}`, `SubscriptionPicker.tsx` 삭제(→ SaleConnectionFields로 교체), `pages/SaleRegisterPage/SaleRegisterPage.test.tsx`, `entities/sale/types.ts`(SaleRegisterRequest.subscriptionId 추가)
- backend: `SaleService`(연결 해석·소유권/일치 검증 분리), `SaleController`(주석), `SaleControllerTest`, `SaleServiceTest`
- docs: `docs/user-guides/manual/{pc,mobile}.html`(매도 등록 안내), 이 work-log

## 동작
- 모든 진입에서 청약 dropdown·'청약 선택' 필수 오류 제거. IPO는 공모주별 진입에서 고정, 문맥 없으면 청약목록 안내만 표시.
- 명시 subscriptionId(URL 프리셋·캡처 매칭)는 소유권 검증 후 그 청약에 연결. 아니면 계좌 선택(1개면 기본, 여러 개면 선택)으로 (투자자·공모주·계좌)를 서버가 조회해 연결.
- 연결된 배정이 있으면 배정수량 입력을 요구하지 않고 남은 수량을 참고 표시. 배정이 없으면 배정수량만 요구하고 등록 시 청약/배정을 자동 생성·재사용(중복 생성 없음).
- 계좌·청약이 바뀌면 연결 기반 기본값(수량·단가·매도일·수수료)을 재계산하되 dirty(사용자 입력)는 유지, 배정수량은 항상 비움.
- 검증: 매도수량/매도일/상한/음수/소유권/트랜잭션 롤백 유지. 소유권은 Sale*/청약 경로 모두 403/404, 명시 id와 IPO·계좌 불일치는 차단(소유권 먼저 확인 후 불일치 400).
- 캡처: 공모주가 현재 화면과 다른 종목이거나 문맥(공모주)이 확정되지 않았으면 채우지 않고 안내만, 매칭이 1건이어도 현재 화면의 계좌·연결을 유지하고 값만 채운다.

## Master 직접검증 후 3건 최소보완 (2차)
- 필수 조회 상태 명시화: 공모주(ipo)·주관사(insight)·계좌·자동연결(lookup)·상세(detail)의 로딩/오류를 `submitBlockReason`으로 노출하고, 하나라도 미완료·실패면 등록 버튼 disabled + onSubmit 조기 반환 + 화면에 안내(실패 시 '다시 시도' 버튼). lookup 실패를 '기존 연결 없음'으로 간주하지 않음. 회귀: lookup 실패/지연·detail 지연·IPO 지연·주관사 실패 시 POST 0회(SaleRegisterForm.test.tsx).
- 문맥 변경 정리: URL 문맥(공모주·subscriptionId) 키가 바뀌면 이전 계좌·pickedSubscriptionId·연결 기반 값을 지운다(dirty 매도값은 유지). 제출 시 선택 계좌를 허용 계좌(공모주 주관사 일치)와 대조하고 벗어나면 차단(`account-not-allowed`). 회귀: createMemoryRouter로 ?ipoId=10→20 이동 시 계좌/연결/배정수량 초기화·dirty quantity 유지(useSaleForm.test.tsx).
- 캡처 문맥 보호: 공모주 확정 전에는 적용 0회(즉시 안내), 1건 매칭이라도 다른 계좌/다른 청약이면 자동 전환하지 않고 값만 채우고 안내. `MatchCandidate.securitiesAccountId` 추가(types는 entities/sale/capture/captureMatch.ts). 회귀: 같은 종목 다른 계좌 선택 후 캡처 → 원래 계좌/연결 유지(SaleRegisterForm.test.tsx), 문맥 로딩 중 캡처 차단.
- 배정방식 제거 등 타 작업자 변경은 그대로 보존. backend 재작성 없음. V45/운영/커밋/푸시 없음.

## 검증
- backend(슬롯 1): `scripts/db-slot.sh reset 1` 후 `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar --rerun-tasks --console=plain` → PASS(exit=0). 테스트 1197건 failures=0 errors=0, checkstyleMain/checkstyleTest/spotlessJavaCheck 0건, bootJar 성공(2m19s).
- UI: `pnpm exec vitest run src/features/sale-register src/fsd-boundaries.test.ts` → 81 passed. 전체 `pnpm exec vitest run` → 927 passed. `pnpm typecheck` PASS, `pnpm lint` 0 errors(기존 4 warnings만), `pnpm build` PASS, 변경파일 prettier 적용.
- `git diff --check` → PASS(이상 없음).

## 남은 사항/주의
- V45 마이그레이션은 배정방식 제거 작업의 것(이 작업에서 DB 스키마 변경 없음).
- 일반진입 IPO selector는 최신 지시대로 미구현. IPO가 필요하면 공모주별 버튼(목록/상세/할 일)으로 진입해야 하고, `/profit-view`·`SubscriptionListPage` 등의 '/sales/register' 링크는 안내 화면으로만 이동한다(공통 버튼 정리는 task_050ccc69bce1 소관).
- 계좌 변경 시 dirty 매도수량은 유지하고 배정수량만 항상 비운다(사용자 입력 보호와 '이전 연결 값 재계산'의 절충 — 구현 판단).
- 커밋/푸시/배포/운영 쓰기 없음. current-status/daily-log는 Master 소관이라 건드리지 않음.
