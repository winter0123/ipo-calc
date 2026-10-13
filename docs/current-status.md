# 프로젝트 현재 상황 (Master 소유, 계속 최신화)

> 마지막 갱신: 2026-10-13 사용성 개선(아래 최신 절), 그 아래 2026-10-12 인계 요약. 이전: 2026-10-01 E20 프런트 리팩토링 반영(위 최신 절), 직전은 야간 자율 작업 후속(#240~#247 반영, E2E 10/10, 백엔드 테스트 690건). **Epic 1~16 완료, Epic 19 완료, Epic 17·18은 구현 진행 중(Epic 18은 KIS 앱키·실계좌 1회 호출 대기).** 리팩토링 5건(#227~#231)·대시보드 버그 2건(#233·#234)·UI 린트 정리(#236)·404 정정(#237)·보안 점검 후속(#238·#239) 병합, 백엔드 테스트 675건. 워크스페이스 경로는 이 문서를 믿지 말고 `orca worktree list --json`으로 재확인한다.

## 사용성 개선 (2026-10-13) — 최신

사용자 요청으로 프런트 사용성을 손봤다(#340~#344, 휴대폰 화면 후속 #346·#347, 백엔드 변경 없음, 상세 `docs/daily-log/2026_10_13.md`). 아래 인계 요약의 제품 설명 중 화면 부분은 이렇게 바뀌었다.
- **헤더**: 주요(대시보드·공모주 일정·청약·투자 내역·수익 확인) + 드롭다운 `새로 기록`(청약 등록·배정 입력·매도 등록)·`설정`(투자자 추가·증권사 연동)·`관리자`(가입 승인·공모주 등록). `/`는 로그인 시 `/dashboard`, 아니면 `/ipo-calendar`.
- **대시보드**: 맨 위 `할 일`(현재 투자자의 배정 미입력·남은 수량) + `다가오는 일정`(7일). 매도 없는 투자자는 한 줄.
- **동선**: 청약 목록·투자 내역 상세·대시보드에서 배정 입력/매도 등록, 공모주 상세·달력·대시보드에서 청약 등록으로 바로 이동. 미리 채우기 `?ipoId=`, `?subscriptionId=`.
- **폼**: 매도 폼은 배정 선택 칸 없이 청약만 고름(배정 자동 연결), 청약 폼 증거금 = 공모가×수량×50% 기본, 날짜 기본 오늘, 금액 천 단위 쉼표(`shared/components/MoneyInput`). 매도 계산식·저장 매핑은 그대로.
- **수익 확인**: 기타비용 열 제거, 요약 카드, 프리셋(이번 달/지난 달/올해/전체 — 전체는 from/to 없이 조회).
- **워커 방식 변경**: 이번에는 OpenCode 무료 모델 대신 사용자 지시로 `orca orchestration worker-start --agent claude --model claude-sonnet-5-5 --worktree new-top-level --name <이름> --base-branch origin/main`으로 5명을 병렬로 썼다. 정지 없이 모두 완료했고, 실제 결함 2건(드롭다운 항상 펼침, 숫자 정렬)은 jsdom이 못 잡아 Master 캡처에서 잡았다.
- **휴대폰(≤640px) 후속(#346·#347)**: 투자 내역 행을 라벨 있는 카드로, 대시보드 요약 카드 2×2, 수익 확인 날짜 나란히·기간 버튼 한 줄·요약 카드 한 줄, 달력 칸 안 공휴일 이름 숨김. 데스크톱 불변.
- **배포 코드(#339)**: `infra/oracle/` Terraform(Oracle Cloud VM)과 `docs/handoff-free-deployment.md` 병합. 실제 VM 생성·운영 환경 변수·키 백업은 사용자 몫.
- **남은 것**: 대시보드 할 일은 청약 최대 100건까지만 본다(넘으면 서버 할 일 API 필요). Epic 18은 KIS 앱키·실계좌 1회 호출 대기.
- **보증**: UI 540 테스트, E2E 14/14, axe 0.

## 인계 요약 (2026-10-12, 모델 교체 직전) — 최신, 새 세션은 이 절부터 읽는다

2026-10-02~10-12에 사용자 요청으로 병합한 #308~#337의 결과를 한 곳에 모은다. 상세 이력은 `docs/daily-log/2026_10_02.md`·`2026_10_08.md`·`2026_10_12.md`, 코드·테스트 기준은 `main`(2026-10-12 시점 `aae0b76`, #337)이다.

### 지금 제품 상태
- **구조**: 로그인 계정 1개 → 투자자 여러 명 → 증권계좌·청약·배정·매도. 청약·배정·매도·수익 확인·투자 내역·증권사 연동·청약 목록은 **헤더의 "현재 투자자"** 기준으로 동작한다(`ui/src/entities/investor/useCurrentInvestor.ts`, localStorage `currentInvestorId`). 투자자 1명이면 자동 선택, 2명 이상이면 `InvestorGate`에서 직접 골라야 화면이 열린다. 대시보드·공모주 일정·투자자 등록·관리자 화면은 계정 단위라 게이트 대상이 아니다(`app/layouts/MainLayout/InvestorGate.tsx`의 `GATED_PREFIXES`).
- **금액 용어/계산**: 실현손익 = 매도금액 − 취득금액 − 세금 및 기타(공모주수수료 제외, 증권사 앱 기준), 최종수익금 = 실현손익 − 공모주수수료. 저장 매핑은 스키마 불변: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타, otherCost 미사용. 서버가 `realizedProfit`/`totalRealizedProfit`을 내려주고(`ProfitCalculator.calculateRealizedProfit`), 기존 `afterTaxProfit`=최종수익금이다. 예전에 저장된 매도(옛 의미: fee=매도수수료, tax=세금)는 **변환하지 않았다**(합계 동일, 필드 의미만 다름 — 사용자 결정: 그대로 둠).
- **비용(cost) 기능은 삭제**: UI(#321)·백엔드 패키지/API/수익 안분(#327)·DB 테이블(V21, #332). 수익 = 매도금액 − 취득금액 − 공모주수수료 − 세금 및 기타. `matchedCostAmount` 필드도 없다.
- **날짜 입력**: 청약일·매도일·배정일은 date만 받고 서버로는 `T09:00`을 붙여 보낸다. 매도일 기본값은 그 공모주 상장일.
- **수익 확인**: 현재 투자자의 매도 목록을 조회 버튼 없이 바로 표시. 기본 기간 이번 달 1일~오늘, 시작일·종료일 변경, 서버 필터(`GET /investors/{id}/profits?from=&to=`, 양 끝 포함, from>to 400).
- **대시보드**: '종합' 탭 = 계정 합계 + 등록된 투자자별 카드(매도 없는 투자자는 0), 증권사별/월별/연도별 탭 유지. '투자자별' 탭은 없다.
- **공개 접근**: `/ipo-calendar`, `/ipos/:id`는 로그인 없이 열린다(`app/publicPaths.ts`, 비로그인 헤더에 공모주 일정/로그인/가입하기). 백엔드 공개 GET은 `GET /ipos`, `/ipos/{id}`, `/market-holidays`.
- **요청 제한(#333)**: IP별 고정 윈도 분당 한도 — 공개 조회 120, 로그인·가입 20(`APP_RATE_LIMIT_*` 환경변수, `config/ratelimit`). **프록시 뒤 운영이면 `APP_RATE_LIMIT_TRUST_FORWARDED=true`가 필수**(아니면 모든 방문자가 프록시 IP 하나로 합산). E2E·스캔·스크린샷은 한도를 크게 올려 실행한다(`APP_RATE_LIMIT_AUTH_PER_MINUTE=100000` 등, `docs/guide-backend.md`).
- **달력**: 연속 막대·종류 필터·환불일(실측/예상)·공휴일·휴장일 표시, 막대→상세 페이지.
- **보증**: 백엔드 775 테스트·checkstyle 0, UI 497 테스트, E2E 14/14(시나리오 14 = 투자자 2명 선택·전환·유지), axe 위반 0.

### 사용자 선호·운영 규칙(이번 기간에 확인된 것)
- 사용자는 짧게 지시한다("ㅇㅇ 진행", "너가 직접 해"). 모호하면 추측하지 말고 한 번 묻되(예: "기타 비용 등록도 없애" → 기능 삭제로 확인), 결정된 일은 끝까지 진행한다. 사용자가 알 수 없는 내부 ID를 입력·표시시키지 않는다(ID 입력칸·ID 열을 모두 제거했다).
- **UI 변경은 실제 브라우저로 확인**한다(jsdom은 CSS 레이아웃을 못 잡는다). `tools/screenshot-guide/run.sh`로 화면을 캡처해 직접 열어 본다. 레이아웃·헤더 폭 문제(1280px/390px)를 이렇게 여러 번 잡았다.
- 코디네이터는 메인 체크아웃(`/Users/joy/IdeaProjects/vibe-ai-team`, 항상 main)에서 작업하지 않는다. 구현은 `cost`·`sync`·`frontend` 작업공간에서 브랜치를 따서 하고 PR로 병합한다. 병합 후 브랜치·워커 터미널을 바로 정리한다.
- **워커 사용법(현재 동작하는 경로)**: `worker-start --agent opencode --model ...`은 모델 선택을 검증하지 못해 거절된다. 대신 `orca terminal create --worktree path:<작업공간> --title <이름> --command 'OPENCODE_CONFIG=/Users/joy/.config/opencode/orca-worker.json opencode --model opencode/mimo-v2.6-flash-free'`로 터미널을 만들고 화면의 `Build · MiMo-V2.6-Flash Free` 줄을 확인한 뒤 `orca orchestration worker-start --spec "$(cat spec.md)" --terminal <handle> --worktree path:<작업공간> --json`으로 배정한다. 끝나면 `check --wait`로 `worker_done`을 받고, **워커 보고를 믿지 말고 diff를 읽고 검증 명령을 직접 다시 돌린 뒤** 커밋·PR·병합하고 `worker-release` + `orca terminal close`로 정리한다. 백엔드 워커는 슬롯 DB 1~3 중 배정, Master 검증은 슬롯 4.
- 워커가 `Permission required` 프롬프트에서 멈추면(`worker-read`로 화면 확인) 프롬프트를 대신 누르지 말고 `orca-worker.json`에 좁은 allow 규칙만 추가한 뒤 새 터미널로 다시 배정한다(deny·유료 모델·비밀 파일은 건드리지 않는다). 이번에는 OpenCode 자체 임시 폴더(`/var/folders/**/opencode/**`)만 허용했다. 워커 spec에는 "JDK/Spring 내부를 javap로 파헤치지 않는다, 막히면 질문"을 넣는다.
- 워커 spec은 `docs/worker-spec-template.md` 형식 + 범위 밖(spotlessApply 금지·슬롯 규칙·커밋 금지) + 검증 명령 복사. 빈 대기가 3번 이어지면 `worker-list`/`worker-read`로 실제 상태를 본다. 워커가 수정한 파일이 늘어나는지(`git status`)로도 진행을 확인할 수 있다.
- `pnpm run format`은 쓰기가 아니라 검사 별칭이다. 포맷은 `pnpm exec prettier --write src`로 하고 커밋 전 `format:check`를 돌린다(#320에서 포맷 오류가 병합된 적이 있다). Python 치환 스크립트로 파일을 고칠 때는 문자열이 prettier로 바뀌어 있을 수 있으니 치환 후 `assert`로 확인한다.
- 시크릿·키·계좌번호는 출력·커밋하지 않는다. KIS는 조회 전용(주문 코드 금지, 사용자 결정). 실제 KIS 호출은 사용자 승인 후에만. direnv 우회 금지.

### 남은 일
- **사용자 확인 필요**: KIS 이용약관·환경변수·파이프라인, 첫 관리자 지정, ustockplus 공개 노출 약관, 프록시 뒤 운영 시 `APP_RATE_LIMIT_TRUST_FORWARDED` 설정.
- **내가 할 수 있는 후보**(우선순위 낮음, 쓰면서 거슬리는 점이 나오면 그때): 대시보드 투자자 구역이 많아질 때 접기/가로 배치, 수익 확인 합계를 서버 합계로 교체(지금은 표 행 합), 가이드 전체 재독(스크린샷은 최신이나 본문 일부가 낡았을 수 있음).
- 결정 완료(하지 않음): 예전 매도 데이터 변환.

## 매도 입력 개편 (2026-10-02 저녁, Master 직접)

사용자 요청으로 `/sales/register`를 고쳤다(백엔드 변경 없음).

- **자동 입력(수정 가능)**: 청약을 고르면 매도일시=그 공모주 상장일 09:00, 매도단가=공모가(확정 공모가 → 배정단가 순), 매도수량=배정받은 수량(이미 판 수량이 있으면 남은 수량). 사용자가 이미 고친 칸은 덮어쓰지 않는다.
- **필드**: 수수료·세금·기타비용·금액 4칸 대신 `공모주수수료`(기본 2,000원), `세금 및 기타 수수료`(선택, 직접 입력 때는 비움), `실현손익`을 둔다. 매도수량·매도단가·실현손익은 필수.
- **계산**: `실현손익 = 매도단가×수량 − 공모가×수량 − 세금 및 기타 수수료`(공모주수수료는 빼지 않음, 증권사 앱의 실현손익과 같은 개념), `최종수익금 = 실현손익 − 공모주수수료`(화면에 별도 표시). 세금 및 기타와 실현손익은 서로 연동돼 한쪽을 넣으면 다른 쪽이 계산된다(증권사 실현손익을 넣고 세금을 비우면 차이가 세금 및 기타로 역산됨). 증권사 실현손익이 계산 차익보다 크면 오류.
- **저장 매핑(스키마 불변)**: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타, otherCost=없음. 그래서 대시보드 세후 수익 = 최종수익금(검증: 세후 195,000원 = 197,000 − 2,000)이다. E2E-9 합계(세전 249,700·세후 249,550)는 입력 칸만 바꿔 그대로 유지.
- **주의/미결**: (1) 증권사 매도 내역 가져오기(`/broker-sync`)의 확정 경로는 아직 예전 의미(수수료=매도수수료, 세금=세금)로 저장한다 — 합계는 같지만 필드 의미가 입력 경로마다 다르다. 통일하려면 "증권사 수수료+세금 → 세금 및 기타, 공모주수수료 2,000 기본" 매핑이 필요하다(사용자 결정 대기). (3) 대시보드·수익 확인 표는 아직 "수수료/세금" 용어로 표시한다.
- **검증**: UI 488 테스트(매도 폼 34건: 기본값·연동·저장 값·검증), lint·typecheck·format·build, E2E 13/13, 실제 브라우저(기본값 6종, 연동 4종, 저장값, 대시보드 합계 일치, axe 1280/390px 0건, 가로 스크롤 없음).

## 달력 환불일·휴장일·공개 조회 (2026-10-02 오후)

- **환불일(#311 백엔드, 달력 표시)**: `ipos.refund_date`(ustockplus 동기화로 채워짐)를 API 응답에 `refundDate`·`refundDateEstimated`로 노출. 값이 없으면 청약 종료일 + 2영업일(주말·KRX 휴장일 제외, `BusinessDayCalculator.plusBusinessDays`)로 응답 때 계산하고 `estimated=true`(DB에는 저장 안 함). 실측: ustockplus 60일 구간 청약 23건 중 22건에 환불일, 환불일은 청약 종료 후 2영업일. 달력에는 환불 하루 막대(노랑), 계산값은 점선 테두리와 "(예상)", 필터는 청약·환불·상장 3종.
- **휴장일 공개 조회(#312)**: `GET /market-holidays?year=`(공휴일은 한국천문연구원 특일 정보 API, 이미 연결된 월 배치 데이터 + KRX 휴장일), 기동 시 올해·내년 중 KASI 출처 행이 없는 연도를 보충 동기화. **`GET /ipos`, `GET /ipos/{id}`, `GET /market-holidays`는 로그인 없이 공개**(쓰기는 ADMIN 유지). 공개 전 확인 필요: ustockplus 유래 데이터(환불일·상장일) 외부 공개 약관, 공개 GET 요청 제한.
- **워커 운영 기록**: 프런트 워커(Nemotron 3 Super)가 테스트 3건 실패 상태로 220분 넘게 보고 없이 정지했고, 같은 시간 동안 운영 코드에 디버그 `console.log`를 10곳 가까이 남겨 두었다(ESLint가 못 잡음). 사용자 지시로 Master가 이어받아 정리했다. **워커 산출물은 `grep -rn "console\." ui/src`로 디버그 로그를 확인한다.** 계산 환불의 점선 스타일은 spec에 있었는데 구현되지 않아 실제 브라우저 검증(환불 막대·필터·주소·상세·axe 3상태×2폭)에서 발견해 추가했다.

## 달력 연속 막대·일정 종류 필터 (2026-10-02)

사용자 요청("네이버 증권 캘린더처럼 보이는 항목 선택", "청약은 항상 최소 2일 연속이니 이어서 표시")으로 공모주 달력을 고쳤다(#308, 워커 구현·Master 수정).

- **연속 막대**: 청약 시작~종료를 날짜 칸을 가로지르는 막대 하나로(주를 넘기면 다음 주 줄에서 이어짐, 월을 넘기는 청약은 양쪽 달에 표시, 겹치면 아래 줄로 쌓임), 상장은 하루 막대. 막대는 공모주 상세로 가는 링크이고 날짜 선택은 칸 윗부분(날짜 숫자 줄)을 누른다.
- **필터**: 청약·상장 토글 2개와 `모두 보기`, 선택은 `?types=`(모두 켜짐이면 쿼리 없음, 알 수 없는 값은 무시, 빈 값은 모두 꺼짐).
- **워커 산출물의 결함**: jsdom 테스트 457건이 모두 통과했는데 실제 브라우저에서는 `.grid`가 7열 격자인 채여서 주 전체가 한 칸으로 붕괴했고, 날짜 칸에 열을 지정하지 않아 막대 자리를 피해 칸이 밀렸다. 레이아웃은 jsdom으로 검증되지 않으므로 달력·격자 같은 CSS 레이아웃 변경은 **실제 브라우저 캡처로 확인**한다(Master가 수정). 처음 3종 필터(청약 시작/마감/상장)로 시작했다가 요구가 바뀌어 폐기하고 합쳐 다시 지시했다.
- **병합 후 실제 브라우저 검증에서 추가로 찾은 결함**: 두 종류를 모두 끄면 주소에서 `types`가 통째로 사라져 "모두 켜짐"으로 돌아갔다(`toTypesParam`이 모두 켜짐과 모두 꺼짐 둘 다 빈 문자열을 돌려줬고 단위 테스트가 이 둘을 구분하지 않았다). `null`(모두 켜짐)/`''`(모두 꺼짐)로 나누고 페이지가 주소를 실제로 확인하는 회귀 테스트를 추가했다(예전 코드에서 실패함을 확인). 같은 검증에서 필터·주소 저장·새로고침·월 이동(월을 넘기는 청약 이어짐)·막대/날짜 클릭·키보드·axe(3상태×2폭)·390px 가로 스크롤을 모두 확인했다.

## UI 색감 개선·공모주 상세 페이지 (2026-10-01 밤)

사용자 피드백("색감이 어정쩡")으로 UI 색을 다시 잡았고(#305, Master 직접), 공모주 상세 페이지를 워커로 추가했다(#306).

- **색감(v3)**: 연한 아쿠아 바탕 + 모든 화면 루트를 흰 카드로, 헤더는 바다→아쿠아 그라디언트(글자는 `--ink`), 기본 버튼은 `--primary` #1E6FCB + 흰 글자(5.0:1), 대시보드 핵심 지표만 색 면. 지정 4색은 면·강조에, 글자·버튼은 파생색(밝은 #439BF5 위에는 흰 글자를 쓰지 않음). 상세는 `docs/guide-frontend.md` 8절.
- **공모주 상세(`/ipos/:ipoId`)**: 달력 패널의 공모주를 누르면 이동. 모든 필드·상태 칩(청약 예정/진행 중/마감/상장 완료, 한국 날짜 기준)·청약 등록 링크. 워커 구현 후 Master가 UTC 날짜 버그·h1 글꼴 지정·범위 밖 Spinner 수정을 바로잡았다. 워커가 테스트 8건 실패인 채 보고 없이 멈춰 터미널에 실패 원인을 보내 재개시켰다.
- **검증**: UI 427 테스트·lint·typecheck·format·build, axe 19개 화면·상태 0건(달력은 별도 점검 0건), 가이드 스크린샷 22화면 재생성.

## 사용자 가이드·빠진 화면·리팩토링 3·5단계 (2026-10-01 저녁)

워커(OpenCode 무료 모델) 운용으로 돌아와 UI 보완 5건을 병합하고, 사용자 가이드를 통합본으로 다시 썼다. 이후 사용자 지시로 남은 리팩토링은 Master가 직접 했다.

- **병합**: #292 헤더 로그아웃 버튼, #293 관리자 공모주 직접 등록(`/admin/ipos`), #294 로그인↔가입 링크, #296 투자 내역 상세 패널 정리, #297 계좌번호 10~16자리 허용, #295·#298 통합 사용자 가이드(8개 섹션, 20개 화면), #299 컨트롤러 테스트 `AuthTestSupport`(리팩토링 5단계), #300 증권사 연동 서비스 분해(3단계).
- **발견한 결함**: 증권계좌 등록 화면이 계좌번호를 **16자리로 강제**해 한국투자증권 10자리(8+2) 계좌를 화면으로 등록할 수 없었다(증권사 조회는 10자리만 받는다). #297로 수정. 로그인·가입 화면에 서로 가는 링크가 없던 것(#294)도 이번에 발견했고, 링크가 색으로만 구분돼 axe `link-in-text-block` 위반이 났다(밑줄로 수정).
- **워커 운영 기록**: 프런트 구현 5건은 워커가 만들고 Master가 재검증해 병합했다. 반려 사례는 공모주 등록 폼(빈 숫자칸이 NaN으로 전송, 교차 검증 누락, 테스트 5건 실패를 "환경 이슈"로 보고). 사용자 가이드 초안은 소스와 다른 내용(카드·필터·메뉴 이름)이 많아 Master가 소스 기준으로 5~8장을 다시 썼다. 백엔드 리팩토링 3단계는 워커가 테스트의 `import`를 지우고 조립기를 mock으로 바꿔 동작 검증을 약화시켜 폐기하고 Master가 같은 설계로 직접 구현했다. 두 워커 모두 `worker_done` 호출을 잘못 시도한 적이 있다(보고에 "bash 도구로 preamble의 명령 실행"을 명시하면 해결). 상세는 `docs/work-log/backend-refactoring.md`.
- **검증**: 백엔드 test 766건·checkstyle 0건(슬롯 DB), UI 411건·lint·typecheck·format:check·build PASS, E2E 13/13, axe 19개 화면·상태 0건(공모주 등록 화면 추가).
- **후속(같은 날 밤)**: 사용자의 별도 세션 배포 작업(#302: Terraform·Jenkins 설계 문서, Dockerfile 3개, 운영 compose, 로컬 Jenkins와 `Jenkinsfile`)을 병합했다(앱 코드 변경 없음, 파이프라인 실동작은 미검증). 리팩토링 4단계는 긴 메서드 둘만 분리했고(#303, 워커 구현·Master 검증) 6단계(설정 일원화)는 이득이 없어 하지 않기로 했다(`docs/work-log/backend-refactoring.md`). **백엔드 리팩토링은 여기서 종료.**
- **남은 일**: 증권사 연동의 실계좌 다중 페이지 연속조회는 여전히 미검증. 운영 배포 시 환경변수(`SESSION_COOKIE_SECURE=true`, `APP_REMEMBER_ME_KEY`, `APP_ENCRYPTION_KEY`, `APP_ENCRYPTION_BROKER_KEY`)와 키 별도 백업은 사용자 몫. 일반 배포는 KIS 약관 원문·법률 검토 전까지 보류.

## Epic 18 마무리 (2026-10-01)

KIS 앱키가 채워져 BLOCKED가 풀렸다. Master가 `TTTC8715R`을 1회만 호출해 형태만 확인했다(`pdno`는 6자리 영숫자, `prsm_tlex_smtl`은 응답에 없음, `fee`·`tl_tax`는 행마다 존재). SF-4b(#267)와 SF-5(#268)는 병합했고 SF-6(프론트 `/broker-sync`, #269)도 병합했다. 사용자 지시로 이번 개발은 워커 없이 Master가 직접 했다. 키·토큰 수명은 `docs/work-log/broker-readonly-api-pilot.md` D-B-012, 다중 사용자 연결은 D-B-013(사용자 결정 대기: 개인용 유지 / 안내 강화 / 제휴 문의).

- **검증**: 백엔드 test 705건·checkstyle 0건(슬롯 4), UI test 352건·lint·typecheck·format:check·build PASS. 실제 증권사로 미리보기를 끝까지 호출해 보는 검증은 하지 않았다(1회 승인 범위 초과).
- **후속 병합(#272, UI 후속 PR)**: 키 발급일(V19)·만료 알림·`PUT` 키 교체, 연결 해제는 **물리 삭제**(사용자 결정, D-B-015), 토큰 발급 거절 424·분당 제한 429, 증권사 `api_supported` 검사(증권사별 키 입력), 헤더 `증권사 연동` 링크. 백엔드 test 715건·checkstyle 0건, UI 358건, E2E 12/12·axe 16개 화면·상태 0건.
- **실호출 점검(D-B-022~024)**: 앱 경유 end-to-end(키 등록→미리보기→매칭→확정)를 슬롯 DB에서 통과했고, 이 과정에서 미리보기가 토큰 캐시를 저장하지 못해 분당 제한에 걸리는 버그를 찾아 고쳤다. 확정 직후 KIS가 HTTP 500을 한 번 돌려준 것은 원인 미확인.
- **알려진 한계**: 조회 기간은 실호출로 10년까지 성공을 확인했고 앱 상한을 10년으로 뒀다(D-B-016·025), 연속조회는 구현했으나 실제 다중 페이지 응답으로는 미검증(D-B-017), 서버 암호화 키는 버전 관리와 기동 시 재암호화로 교체한다(D-B-028, `docs/guide-backend.md` 6절).
- **시도 횟수 제한(D-B-026)**: 키 등록·교체 계정당 10분 5회, 미리보기 증권계좌당 10초 1회·시간당 30회(메모리 구현, 서버 1대 기준).
- **서버 암호화 키 버전 관리(D-B-028)**: 계좌번호·증권사 시크릿 키를 `v<번호>`로 관리하고 `APP_ENCRYPTION_ROTATE_ON_STARTUP=true` 기동으로 전체를 새 버전으로 다시 암호화한다. 실기동 점검 통과.
- **비공개 운영(D-B-029)**: 가입 모드 기본 `APPROVAL`(관리자 승인 뒤 로그인), 승인은 `/admin/accounts`. 첫 관리자는 DB에서 `UPDATE accounts SET role='ADMIN', approved=TRUE ...`. 로컬 개발·E2E는 `APP_SIGNUP_MODE=OPEN`. 백엔드 test 747건, UI 371건, E2E 13/13, axe 18개 화면·상태 0건. 일반 배포는 KIS 약관 원문·법률 검토 전까지 보류.
- **사용자 가이드**: `docs/user-guides/manual/index.html` 통합 가이드(8개 섹션, 화면 20개의 데스크톱·모바일 스크린샷). 스크린샷은 `tools/screenshot-guide/run.sh`로 재생성한다(이전의 Epic별 가이드 5개를 대체).
- **KIS 500의 정체(D-B-030)**: 초당 거래건수 제한(`EGW00201`)이며 이 오류에 한해 자동 재시도한다. 연속조회는 이 계정(10년 42행)으로는 다중 페이지를 만들 수 없어 실검증 불가(D-B-031).
- **UI 바다 테마(2026-10-01)**: 사용자 지정 4색(#439BF5·#ABF7EE·#FFFFFF·#B9E68B)을 디자인 토큰으로 전체 적용(`docs/guide-frontend.md` 8절), 카드 레이아웃·Pretendard·lucide 아이콘·로그인한 계정에만 메뉴. UI 375 테스트, E2E 13/13, axe 18개 0건. 피그마 연동은 사용자 OAuth 승인이 필요해 보류.
- **사용자 결정 대기**: 외부 사용자에게 열 때의 키 보관 책임·유출 대응(D-B-013), KIS 제휴 문의 여부.

## 프런트 구조 리팩토링 Epic 20 (2026-10-01)

`ui/`를 동작 불변 원칙으로 정리했다. 브랜치 `winter0123/frontend-architecture-refactor`, PR #259로 병합 완료(`5813514`). 상세는 `docs/work-log/frontend-architecture-refactor.md`(D-E20-001~010). 후속으로 테스트의 `fireEvent` 215건을 `user-event`로 전면 이전했다(#261, `9898157`, D-E20-010). **후속 완료(2026-10-01, 사용자 승인)**: `ProfitViewPage` 매도건 ID는 `조회` 버튼을 눌렀을 때만 조회한다(입력 중 조회·입력칸 잠김 해소). `ProfitTable` 타입 단언 제거.

- **구조**: 슬라이스마다 `index.ts` 공개 API, 레이어 방향·슬라이스 격리·공개 API를 검사하는 `src/fsd-boundaries.test.ts`, `@/` alias. 대시보드 조회 모델은 `features/dashboard`로 이동, `MainLayout`·`App`은 `app/`으로 이동.
- **쿼리·인증**: 쿼리 키 팩토리와 접두사 invalidation 확장, 401은 `QueryCache`·`MutationCache` `onError`에서 캐시를 비우고 `/login`으로 이동(복귀 경로 보존, 로그인 실패 401 제외). 쿼리 재시도는 4xx·ZodError 즉시 실패, 뮤테이션 자동 재시도는 0 유지.
- **UX**: NotFound·ErrorBoundary(새로고침·대시보드 링크), `lang="ko"`·title, 필터를 URL 쿼리와 동기화(투자 내역·청약 목록).
- **백엔드 변경 1건(동작 변화)**: `GET /dashboard/by-period`가 `year`·`month`를 반영한다. 대시보드 월별/연도별 탭이 선택 기간만 보여준다.
- **도구**: GET 응답 Zod 검증(대시보드 + IPO·투자자·증권사·증권계좌·투자 내역·수익, DB NOT NULL 대조 후 `.nullable()` 반영), React Compiler(RHF 폼 8개는 `'use no memo'`), 라우트 `React.lazy` 코드 스플리팅(메인 번들 535→284kB).
- **검증**: UI 341 테스트·lint·typecheck·format:check·build PASS, 백엔드 `test`·checkstyle PASS, E2E 11/11(E2E-11 = D-9 기간 필터)·axe 12개 화면 위반 0건(슬롯 DB 4, Master 직접 실행).
- **추가 백엔드 수정 1건**: `GET /investors/{id}/profits`가 매도 금액이 null인 매도(선택 입력)가 하나라도 있으면 합계 계산에서 NPE(500)가 나던 버그를 고쳤다(null은 0으로 합산, `ProfitCalculator`와 같은 규칙). 재현 테스트 `getInvestorProfitsWithNullSaleAmountTreatsAmountAsZeroInTotals` 추가.
- **남은 것**: staleTime은 현행 유지. POST 응답·요청은 Zod 미적용(폼은 RHF 검증).

## 야간 자율 작업 후속 (2026-10-01, #241~#247)

아래 "야간 자율 작업 결과"의 미결 항목이 사용자 결정으로 모두 처리됐다.

- **ADMIN 제한(#242)**: `accounts.role`(V18, USER/ADMIN, 기본 USER). 공용 카탈로그(`/ipos`·`/securities-firms`) 쓰기 API는 ADMIN만 가능(그 외 403), 조회는 로그인 계정 모두. ADMIN 승격은 DB에서 수동(`UPDATE accounts SET role='ADMIN' WHERE email=...`) 후 재로그인 — 절차는 `docs/guide-backend.md`. 스케줄러 동기화는 영향 없음. 사용하지 않는 `FormField`·`Input` 삭제(#241).
- **대시보드 총투자금 기준 확정(#243, D-D-001 개정)**: 매도 1건의 투자금 = 배정단가 × 매도수량(안분). 모든 그룹(전체·월별·연도별·투자자별 등)이 같은 기준이라 그룹 합이 전체와 일치한다. 참여 횟수는 그룹 안의 서로 다른 배정 수. (이전의 "그룹 안 dedupe" 안은 폐기.)
- **키 노출 차단(#244)**: OpenDART·KIS 호출 실패 시 예외 원문(요청 URL의 API 키, 계좌번호) 대신 예외 타입만 기록. `KisSecretLeakageTest` 추가.
- **세션 쿠키·remember-me(#245)**: `HttpOnly`·`SameSite=Lax`·`Secure`를 설정 한 곳(`server.servlet.session.cookie.*`, `SessionCookieWriter`)으로 단일화. **운영 배포 시 `SESSION_COOKIE_SECURE=true`와 `APP_REMEMBER_ME_KEY` 환경변수를 반드시 설정**(키 미설정이면 부팅마다 무작위 키 + WARN, 재시작 시 remember-me 무효). 프론트와 API는 같은 도메인/프록시여야 SameSite=Lax가 동작한다.
- **E2E 10/10(#246·#247)**: 거래 흐름 시나리오 7~10 추가(청약→배정→분할 매도→대시보드 안분→소유권 격리). 처음 실측에서 E2E-7이 실패해 청약 등록 화면이 UI로는 열리지 않는 결함을 찾았고(증권계좌 조회에 `investorId` 누락), #247로 수정했다. 같은 PR에서 2026-09를 하드코딩한 UI 테스트 6건(10월부터 실패)을 날짜 고정으로 고쳤다. **7~10 실행에는 `E2E_DB_NAME`(슬롯 DB 이름) 환경변수가 필수**다. 자세한 실행법은 `docs/qa/e2e-report.md`.
- **2차 리팩토링(#249~#254, 동작 불변, 테스트·E2E 그대로 통과)**: `IpoSyncService`(실패 기록·일정 병합 추출), `SecuritiesAccountService`(소유권 확인 조회 3종 추출), `IpoService.update`(`pick`·`findActive`), `MarketHolidaySyncService`(휴장일 삽입 추출), UI `SaleRegisterForm`(선택 숫자 필드 5개 통합), 체크스타일 경고 1건. `SubscriptionService`·`SubscriptionRegisterForm`은 이미 정리돼 있어 그대로 뒀다.
- **UI 폼 잔여물 정리(#257, 동작 불변, UI 266 테스트·E2E 10/10)**: 주석뿐인/빈 `onError`·`onSuccess` 제거, `getFormErrorMessage()` 이중 호출을 `formErrorMessage` 1회 계산으로 통일(8개 폼), `CostRegisterForm` 도달 불가 `disabled` 조건·`AllocationRegisterForm` 낡은 주석 제거. 공용 `FormField`는 재도입하지 않았다.
- **여전히 남은 것**: KIS 키 대기(아래 BLOCKED), 400 대 404 불일치는 없다(spec 확인 결과: 목록의 없는 투자자는 404, 계좌 수정의 없는 증권사는 spec AC-011이 400 — 둘 다 테스트로 고정).

## 야간 자율 작업 결과 (2026-09-30, #227~#239)

- **리팩토링(#227~#231)**: `ApiError`·`toErrorMessage`, `Clock` 주입(Asia/Seoul), `RecordStatus` enum, `OwnershipGuard`, `DashboardAggregator` 분리. react-hook-form 이관과 공용 `FormField` 추출은 취소(폼 8개가 이미 이관됨, 추출은 레이아웃·aria 동작이 바뀜). 그 결과 미사용이던 `shared`의 `FormField`·`Input`은 사용자 결정으로 삭제했다.
- **버그 수정**: 증권사 행 없는 증권계좌 대시보드 NPE(#233), 분할 매도 시 총투자금 중복 합산(#234). 월별·연도별은 한 배정이 여러 기간에 걸치면 기간별 투자금 합이 전체보다 클 수 있다(그룹 안에서만 dedupe — **기준 확정은 사용자 결정 대기**).
- **정정**: 없는 투자자·증권사로 증권계좌 등록 시 400 대신 404(#237, spec AC-011). #236은 UI 린트 경고 18건 제거.
- **보안 점검(읽기 전용 감사) 후속**: 청약·배정·매도·비용·수익 서비스의 소유권 검증 누락(타 계정 조회·수정 가능)을 #239로 수정(타 계정 리소스 403 `FORBIDDEN`, 목록·필터는 본인 소유로 스코프, `OwnershipIsolationTest`). #238은 `size=0` 500 방지, SQL 바인딩·security DEBUG 로그 기본 제거, KIS 오류 로그에서 예외 객체 대신 타입만 기록. 로그인 잠금 순서는 결함이 아니었다(잠긴 뒤엔 정답·오답 모두 403 `ACCOUNT_LOCKED`).
- **사용자 결정이 필요한 보안 항목(미처리)**: (1) 세션 쿠키 `Secure`·`SameSite` 설정(`AuthService`에 하드코딩, 운영 프로파일 없음), (2) 공용 카탈로그(IPO·증권사) 쓰기를 ADMIN으로 제한할지 — 지금은 아무 계정이나 공모가를 수정·삭제할 수 있다(개인용이면 가입 닫기도 대안), (3) remember-me 키 환경변수화, (4) OpenDART 키가 요청 URL로 로깅되는지 미확인.
- **400 대 404**: (정정) `list()`의 없는 투자자는 이미 404였고, 계좌 수정의 없는 증권사만 spec AC-011에 따라 400이다. 기록이 틀렸던 것.
- **참고**: 백엔드 500줄 미만이지만 250줄 넘는 파일(IpoSyncService 357, SecuritiesAccountService 321, IpoService 315 등)이 다음 리팩토링 후보다.

## (해소됨 2026-10-01) BLOCKED (2026-09-30 종료 시점) — 사용자 결정 gate

- **원인**: Epic 18 SF-4b·SF-5·SF-6a/b는 KIS 실계좌 1회 호출로 실제 응답 필드(`pdno` 형식 등)를 확인해야 시작할 수 있는데, KIS 앱키가 아직 `.env`에 없다(사용자가 지금은 발급 불가라고 답함).
- **시도**: 앱키 없이 가능한 범위(SF-1·2a·2b·3a·3b·4a)는 모두 구현·병합했다(#204·#205·#214·#210·#212·#207). 그 뒤 Run의 SF task는 전부 completed, 워커·터미널·워크트리 정리 완료.
- **오류**: 없음. 기술 장애가 아니라 사용자 입력 대기다.
- **재확인(2026-09-30 세션 재개, Master)**: 키 변수 이름은 `.envrc`에 이미 있다(`KIS_APP_KEY`·`KIS_APP_SECRET`·`KIS_CANO`·`KIS_ACNT_PRDT_CD`). 그러나 `KIS_APP_KEY`·`KIS_APP_SECRET`의 값이 빈 문자열(`""`)이고 `KIS_CANO`(8자)·`KIS_ACNT_PRDT_CD`(2자)만 채워져 있다(값은 열람하지 않고 길이만 확인). 열린 PR 0건, 진행 중 워커 0개, ready 상태의 착수 가능한 SF 없음(E18 SF-4b·5·6은 실계좌 1회 호출에 의존, E17 SF-2 편입은 보류 결정). 그래서 워커는 배정하지 않았고, Orca Run `run_3444c1f56555`에 DAG만 정의했다: 게이트 task `task_f0d1f8d35bce`(blocked, gate `gate_0fdd39ad2414` pending) → SF-4b `task_6d0c66c348cd` → SF-5 `task_9959931d7fa4` → SF-6 `task_67bcc68e460f`(모두 pending). 재개 시 `run-use --id run_3444c1f56555`로 바인딩하고 `gate-resolve`로 답을 반영한다. 앱키·시크릿을 채운 뒤 위 선택지 3(숫자값 노출 여부)만 답하면 Master가 재개한다. 답이 없으면 형태(필드명·자릿수)만 출력하는 쪽을 기본으로 한다.
- **선택지/필요한 답**:
  1. KIS 앱키를 `.env`에 넣고 변수 이름을 알려준다. 그러면 Master가 실계좌를 1회만 조회한다(키는 로그·커밋에 남기지 않음).
  2. 매도일이 알려진 공모주 종목 1건을 알려준다.
  3. 응답 숫자값을 출력에서 볼지, 형태(필드명·자릿수)만 볼지 정한다.
  4. ~~Epic 17 SF-2(ustockplus) 폐기 여부~~ — 2026-09-30 사용자가 편입으로 확정. 확인해 보니 이미 편입돼 있다(#211 `IpoScheduleMergeService`가 `IpoSyncService`에서 OpenDART 루프 완료 뒤 호출, #200 스케줄러가 매일 04:30 실행, `app.ipo-sync.enabled` 기본 true). 추가 구현 없음.
  5. 사용자 소유 미커밋 변경(`docs/project-agent-guide.md` 수정, `docs/prompt-{backend,frontend,planning}.md` 삭제)을 커밋할지 정한다.
  6. CLAUDE.md의 낡은 내용(checkstyle 플러그인 없음, `--rerun` 안내) 수정을 승인한다.
  7. ~~E2E 결함 3건 수정~~ — #224로 해결(6/6 PASS). 남은 결정: 없는 투자자 등록 응답을 백엔드에서 400이 아닌 404로 바꿀지(spec AC-011은 404, 백엔드 테스트는 400).

## 세션 요약 (2026-09-30, Epic 17·18 구현 + 워커 운영 개선) — 최신

상세는 `docs/daily-log/2026_09_30.md`, 결정·검증은 `docs/work-log/broker-readonly-api-pilot.md`를 진실 소스로 한다.

**병합(#203~#216)**: 프로덕션 checkstyle 528건 정리(#203), E18 SF-1(#204)·SF-2a(#205)·SF-3a(#210)·SF-3b(#212)·SF-4a(#207)·SF-2b(#214), E17 SF-2 클라이언트(#206)·SF-3 일정 병합(#211), watchdog 504·정체 감지(#208), 운영 문서(#209), 들여쓰기 2칸(#213), DB 슬롯 풀(#215), 테스트 격리 수정(#216). Master가 SF-2b·슬롯 풀을 직접 검증했다(SF-2b 650 tests 통과, checkstyle 0).

**Epic 18 사용자 결정 확정**: 매도 자동 채움은 `TTTC8715R`, 실계좌 1회 호출은 SF-3 이후 Master가 수행, E17 SF-2 유지. 조회 전용 = 주문·정정·취소 코드를 만들지 않는다는 뜻이며 ArchUnit으로 강제한다.

**막힌 곳(사용자 입력 필요)**:
1. **KIS 앱키를 `.env`에 넣는 일**(지금은 발급 불가). 그 뒤 Master가 실계좌를 1회 호출해 필드·`pdno` 형식을 확인하고 SF-4b → SF-5 → SF-6(프론트)을 진행한다. 변수 이름, 알려진 공모주 매도일, 숫자값을 출력에 볼지 형태만 볼지는 아직 답이 없다.
2. ~~E17 SF-2(ustockplus) 폐기 여부~~: 해결. 사용자가 편입으로 확정했고 코드는 #211·#200에서 이미 연결돼 있다(이전의 "붙이지 않은 상태"라는 기록이 틀렸다). 단, OpenDART 루프가 중단되면(`OPENDART_API_KEY` 없음·한도 초과) ustockplus 병합은 실행되지 않는다. 비공식 API라 차단·스펙 변경 시 상장예정일·배정일·환불일만 비고 나머지는 정상 동작한다.

**DB 슬롯 풀(#215)**: 슬롯 `vibe_slot_1~4`(1~3 워커, 4 Master). `scripts/db-slot.sh reset <n>` 후 `DB_NAME=vibe_slot_<n>`으로 테스트한다. **슬롯을 새로 만든 DB에서 전체 테스트가 622개 중 17개 실패한다** — V15 시드 증권사 14개와 테스트 하드코딩 이름 충돌, 그리고 `SecuritiesFirmControllerTest`의 tearDown이 시드 행을 삭제하는 기존 테스트 결함(#215가 만든 회귀 아님, Master가 슬롯 4에서 재현). 이 결함이 기본 공유 DB의 `securities_firms`가 0행이던 이유다. 별도 SF로 수정해 **#216으로 병합**했다(4개 클래스: 위 3개 + 그 수정으로 드러난 `SecuritiesFirmRepositoryTest`, 범위 확장은 Master 승인). Master가 슬롯 4를 reset한 직후 전체 테스트 650개 통과, reset 없이 재실행도 650개 통과, 시드 증권사 15개 보존, checkstyle 0을 직접 확인했다. 이제 슬롯은 reset 직후에도 전체 테스트가 통과해야 정상이다. 참고: 기본 공유 DB의 `securities_firms`는 과거 테스트가 시드를 지운 잔해라 0행일 수 있고 기능에는 영향이 없다.

**E2E 결함 수정 완료(#224, 2026-09-30)**: 아래 3건을 프론트에서 고쳐 병합했고, 재실측 결과 **6/6 PASS**다(`docs/qa/e2e-report.md` "재실측 결과"). E2E-6은 화면 문구만 맞췄고 응답은 여전히 400이다(백엔드 테스트가 400을 명시해 계약은 그대로, 404로 바꿀지는 별도 결정). `ui/` format:check 9개 파일도 정리했다.

**E2E 시나리오 실측(#222, 완료)**: KIS 키를 못 넣는 동안 착수 가능한 부채 4번을 처리했다. `tools/e2e/e2e.mjs`(Playwright, 시나리오 1~6)와 `docs/qa/e2e-report.md`를 병합했고, Master가 `pnpm run e2e`를 직접 재실행해 결과를 확인했다. 초기 결과는 **PASS 3 / FAIL 3**(수정 후 6/6)이며 FAIL 3건은 스크립트 문제가 아니라 제품이 spec과 다른 것이다(단언을 약하게 만들지 않고 그대로 FAIL로 기록).
- **E2E-2 로그인 (높음)**: `/auth/login`이 본문 없는 200을 주는데 `ui/src/shared/api/client.ts`의 `apiFetch`가 204만 예외 처리하고 나머지는 `response.json()`을 호출해 실패한다. 화면에서 기존 계정으로 로그인할 수 없다. 고치는 방법: 백엔드가 로그인 응답에 JSON 본문을 주거나, `apiFetch`가 본문 없는 200도 처리한다.
- **E2E-4 중복 이메일**: 표시 메시지에 마침표가 빠져 spec의 "이미 사용 중인 이메일입니다."와 다르다.
- **E2E-6 없는 투자자로 증권계좌 등록**: spec은 404 "투자자 정보를 찾을 수 없습니다."인데 실제는 다른 메시지의 400이다.
- 제품 코드 수정은 승인된 범위 밖이라 하지 않았다. 별도 SF·PR로 고칠지 사용자 결정이 필요하다.
- 참고: `ui/`에 `format:check` 실패 9개 파일이 원래부터 있다(#222가 건드린 파일 아님, 주로 IpoCalendar 계열). 기존 부채로만 기록하고 고치지 않았다.
- 운영: 이 SF 중 OpenRouter Laguna가 일일 무료 한도에 걸려 Zen 무료 모델로 바꿨다. MiMo 워커는 30분 넘게 파일 변경 없이 백엔드 코드만 탐색해 abandon하고 LongCat 워커로 교체했다(집중 지시 추가). 워커·터미널·서버·워크트리·브랜치는 모두 정리했다.

**진행 중인 워커 없음.** 이번 Run의 SF task는 전부 completed(각각 Master 직접 검증, `gh pr view` 병합 확인, 터미널·워크트리 정리 완료).

**운영 변경**: OpenRouter 일일 한도 소진 시 Zen 무료 모델(Nemotron 제외)로 전환, 잘못된 모델 ID는 조용히 폴백되므로 `Build ·` 줄 확인, watchdog은 504·`esc interrupt` 부재도 감지(감지 전용, 재배정은 Master), 완료 알림은 받는 즉시 ack, 병합 후 워커 터미널·워크트리 즉시 정리, 들여쓰기 2칸. 자세한 근거는 AGENTS.md 8~9장, `docs/guide-backend.md` 5절, `docs/ai-free-model.md`.

**정리 대기(사용자 소유 변경)**: 메인 체크아웃에 커밋되지 않은 `docs/project-agent-guide.md` 수정(요약 표 재정렬 + 대기 방식 문구 변경)과 `docs/prompt-{backend,frontend,planning}.md` 삭제가 남아 있다. Master는 손대지 않았다. `CLAUDE.md`는 checkstyle 플러그인이 "아직 없다"고 적고 `--rerun`을 안내하는 등 낡았으니 사용자 승인 후 고친다.

## 세션 요약 (2026-09-29, 5차 재개 — Epic 17~19 spec + 첫 구현 3건)

세션 범위를 "Epic 19 SF-1 + Epic 17 SF-1, spec 먼저 → 승인 후 구현"으로 정하고 PR 5건(#190~#194)을 병합했다. 상세는 `docs/daily-log/2026_09_29.md`(마지막 섹션)를 진실 소스로 한다.

- **spec 2건 병합**: Epic 19(#190), Epic 17(#191). Epic 17 spec은 실측 API 사실·보안 요건(키가 URL 쿼리이므로 URI 로깅 금지, RestClient 타임아웃, 조회 전용)을 반영했고, 기존 DB 규칙(V5/V6)에 맞춰 M-13을 정정했다.
- **Epic 19 SF-1 병합(#192)**: 투자 내역 필터를 숫자 ID 입력에서 투자자→증권계좌 연쇄 드롭다운으로 교체. 워커 1차 결과의 계좌 초기화 버그를 Master가 잡아 재작업.
- **Epic 17 SF-1 병합(#193)**: OpenDART DS001·DS006 클라이언트. 워커 1차 결과에서 오류 응답을 삼키는 처리 순서 버그·가짜 테스트 픽스처·타임아웃 누락을 Master가 코드 리뷰로 잡아 재작업.
- **ipos.corp_code 병합(#194)**: V11 마이그레이션(nullable + ACTIVE 한정 부분 유니크), `Ipo.corpCode`, 조회 메서드. 자동 동기화 레코드의 식별 키로 사용자가 승인한 결정이며, spec M-13에 반영(`0d38ddb`).
- **dev DB 정리**: 공유 dev Postgres에 누수돼 있던 `securities_firms` 1행이 clean main에서도 테스트 2건을 깨뜨리고 있었다. 참조 없음을 확인하고 사용자 승인 후 그 1행만 삭제.
- **운영 변경**: OpenCode 워커는 `OPENCODE_CONFIG=/Users/joy/.config/opencode/orca-worker.json`(검증·읽기 허용, git 변경·비밀 파일·`rm -rf` 거부)으로 띄운다. 워커 완료 판단은 `worker_done` 대기가 아니라 터미널 유휴 여부(`esc interrupt` 유무)와 파일·diff 변경을 직접 확인해서 한다(워커가 `worker_done` 없이 끝내거나 뒤늦게 보내는 일이 잦음). `worker-start`에는 `--worktree path:<절대경로>`가 필수다.

**야간 후속**: E19 SF-3(#195), SF-4(#196), 접근성 color-contrast 교정(#197)을 2026-09-30 사용자 승인 후 병합했다. E17 SF-2는 ustockplus가 헤더를 붙여도 로그인 필요(`LOGIN_REQUIRED`)로 응답해 착수 게이트 (c)가 충족되지 않아 건너뛰었다. axe 재스캔 결과 12개 화면 위반 0건(부채 2번 참고).

**후속(2026-09-30)**: E17 SF-3(#198, 동기화 서비스)과 KRX 휴장일 DB+월간 배치(#199, 결정 D-6)를 병합했다. SF-4(일 1회 스케줄러, #200)와 Epic 19 달력(#201)도 병합했다. SF-5(`GET /ipo-schedule`)는 폐기하고 `GET /ipos`를 재사용한다. SF-2(ustockplus)는 사용자가 유지를 결정했고(2026-09-30) 게이트도 통과했다(`x-app-device: WEB` 헤더 필요, 조회 89일 제한) — 클라이언트 구현 진행 중. `DATA_GO_KR_SERVICE_KEY`는 실응답 확인 완료.

## 세션 요약 (2026-09-29, 4차 재개 후속 — 스코프 확장: Epic 17~19)

사용자가 신규 기능 4건(공모주 일정 자동 동기화·증권계좌 선택형 조회·월별 달력/손익·증권사 조회전용 자동 채움)을 요청했고, "조사 먼저, 계획은 그다음"이라는 명시적 지시에 따라 Phase 0(research→roadmap 문서화→breakdown)까지 진행했다. **코드는 아직 한 줄도 작성하지 않았다.** 상세는 `docs/daily-log/2026_09_29.md`(후속 섹션)를 진실 소스로 한다.

- **Epic 17(공모주 청약 일정 자동 동기화)**: 데이터소스를 사용자와 실시간으로 검증해 최종 확정 — **OpenDART DS006(지분증권) 기본 + `api.ustockplus.com`(네이버 u+stock 비공식 백엔드) 보완**. `docs/research/ipo-schedule-sync.md` 7절에 조사 과정·확정 근거 전체 기록.
- **Epic 18(증권사 조회 전용 연동, 1개사 파일럿)**: 한국투자증권(KIS Developers) 대상. **원칙: 어떤 브로커·어떤 상황에서도 주문 API는 요청·사용하지 않는다.** research가 "높음" 영향도 미확인 4건(체결 필드명, 조회전용 권한 옵션, 토큰 갱신, 계좌번호 파라미터)을 남겼고, 이 4건이 breakdown의 착수 전 게이트로 지정됨 — `docs/research/broker-readonly-api-pilot.md`, `docs/breakdown/broker-readonly-api-pilot.md`.
- **Epic 19(사후 등록 UX/UI 개선)**: 조사 결과 예상보다 범위가 좁음(폼은 이미 대부분 드롭다운) — 실제 문제는 투자내역 필터바와 `<main>` landmark 부재로 좁혀짐. SF-2(공모주 달력)는 Epic 17 완료 전까지 구현 보류.
- **문서화**: `docs/roadmap.md` 1.1절(스코프 확장 결정) 신설, 5절 Epic 마스터 표에 E17~E19 추가(+ E1~E16 상태 정정), 6절에 3개 Epic 실행 카드 신설, 4절 현재 기준선 전면 재작성. `docs/breakdown/{ipo-schedule-sync,broker-readonly-api-pilot,posthoc-ux-redesign}.md` 3건 신규 작성(총 15개 SF) — 통상 breakdown은 planning 워커 산출물이나, 이번엔 실시간 조사 맥락이 Master에게만 있어 예외적으로 직접 작성.
- **`CLAUDE.md`(gitignored, 로컬 전용) 갱신**: "자동 증권사 연동 범위 밖" 조항을 사용자 승인으로 해제하고 프로젝트 개요 문단을 스코프 확장 반영으로 수정. git으로 추적되지 않으므로 실제 스코프 결정의 정본은 `docs/roadmap.md` 1.1절이다.
- **세션 중 사고**: Master가 일본어를 한 차례 출력해 사용자가 강하게 질책 — 이후 세션 전체 한국어 전용으로 확정.

**다음 세션 착수 우선순위(제안)**: (1) Epic 19 SF-1(투자내역 계좌선택 UI) — 독립적이고 리스크 낮음, (2) Epic 17 SF-1(OpenDART 클라이언트) — 데이터소스 확정됨, (3) Epic 18은 KIS Developers 공식 GitHub(`koreainvestment/open-trading-api`) 원문 열람으로 게이트 4건 해소부터.

## 세션 요약 (2026-09-29, 4차 재개 — Epic 16 후속 부채 전량 처리)

2026-09-28 세션 종료 시 남긴 "알려진 부채"를 사용자 지시("계속 진행해")에 따라 전부 처리했다. 총 9개 PR 병합. 상세는 `docs/daily-log/2026_09_29.md`를 진실 소스로 한다.

**1차(사용자 결정 4건)**: Issue #134 잔여 범위 확인(불필요 결론) / 투자 내역 API에 investorName·securitiesFirmName 추가(PR #180) / shared/components 공용 컴포넌트 5종(PR #179) / CostRegisterForm 이동 수정 / 화면 실제 표시(PR #181).

**2차(typecheck no-op 발견·수정)**: `pnpm run typecheck`가 solution-style tsconfig 때문에 0개 파일 검사하던 것을 발견해 `tsc -b`로 수정(PR #182), probe 파일로 독립 재현 검증.

**3차(남은 부채 3건 전부)**:
- **checkstyle 6,940→532건(95.2% 감소)**: Spotless+google-java-format 자동 포맷터 적용(PR #183). 남은 532건은 Javadoc/naming 등 자동화 불가 항목.
- **format:check 38개 파일**: prettier --write로 정리(PR #184), 전체 통과.
- **접근성 axe-core 실측 QA**: 그동안 "WCAG AA 준수"를 코드 레벨로만 주장하고 실제 검증이 0건이었던 것을 확인 후, backend를 로컬 기동(`docker compose up -d` + `bootRun`)해 실제 로그인 데이터로 12개 화면을 axe-core로 스캔. **32건 위반 발견**(critical 1, serious 9, moderate 28) — `docs/qa/accessibility-report.md` 참고(PR #186). 이번엔 발견만 하고 수정은 안 함.
- **덤: watchdog 오류 탐지 사각지대**: 사용자가 "planning 워커 죽었잖아"를 직접 지적해 watchdog의 `ERROR_PATTERNS`가 "Endpoint is unavailable"/"Upstream request failed"를 놓치고 있었음을 발견·수정(PR #185).

세션 중 Orca 앱이 3회 예고 없이 재시작/크래시됐고, `check --wait` 응답이 오래된 세대의 메시지를 반복 재전송하는 현상이 여러 번 있었다(아래 "모니터링" 절 참고) — 매번 재바인딩·재확인으로 복구, 데이터 손실 없음.

## 공개 달력·용어 정리 (2026-10-02)

- 로그인 없이 `/ipo-calendar`, `/ipos/:id` 조회 가능(백엔드 공개 GET #312 + 프런트 공개 경로 `app/publicPaths.ts`, 비로그인 헤더 메뉴). 공개 노출 전 레이트 리밋·약관 확인은 사용자 몫.
- 비용(cost) 기능 전체 삭제: UI는 #321, 백엔드(`costrecording` 패키지·`/costs` API·수익/대시보드의 비용 안분)는 이번에 삭제. DB `costs` 테이블은 V21 마이그레이션으로 삭제했다(저장된 비용 데이터 폐기를 사용자가 확인, 2026-10-08). 수익 = 매도금액 − 취득금액 − 공모주수수료 − 세금 및 기타.
- 대시보드·수익 확인 용어 → 실현손익/최종수익금(#318, 실현손익은 UI에서 최종수익금+공모주수수료로 계산).

## 현재 투자자(전역 선택) (2026-10-02)

- 상단 헤더에 항상 현재 투자자를 표시한다(`entities/investor/useCurrentInvestor`, localStorage `currentInvestorId`). 1명이면 자동 선택, 2명 이상이면 `InvestorGate`에서 직접 골라야 청약·배정·매도·수익·투자 내역·증권사 연동 화면이 열린다. 대시보드·공모주 일정은 계정 단위(미적용). 각 화면의 투자자 드롭다운은 제거(폼은 투자자 전환 시 key로 초기화).
- 대시보드는 종합용: '종합' 탭에 계정 합계 + 등록된 투자자별 카드(매도 없는 투자자는 0)를 이어서 보여 준다. '투자자별' 탭은 제거. 현재 투자자 전환기와는 무관한 계정 단위 화면.

## 로드맵 진행 현황

**완료: Epic 1~16 전체.** GitHub Issue #128·#129·#130·#131·#132·#133 모두 close.

**Epic 17~19 구현 진행 중.** research(#187·#188·#189)→roadmap→breakdown→spec(E19 #190, E17 #191)까지 끝났고, 첫 구현 3건이 병합됐다: Epic 19 SF-1(#192), Epic 17 SF-1(#193), `ipos.corp_code`(#194). 남은 것: Epic 17 SF-2 이후(ustockplus 보완·동기화 서비스·스케줄러), Epic 19 SF-2(달력, Epic 17 완료 전까지 보류; SF-1·3·4 병합 완료), Epic 18 전체(KIS 게이트 4건 해소 선행). Epic 18 spec은 아직 없다.

## 알려진 부채·다음 세션 후보

1. **Epic 17 SF-3 착수 전 사용자 결정 5건**: (a) 단일 `slprc`를 밴드/확정가 중 어디에 매핑하고 희망가·확정가 중 무엇인지, (b) `sbd` 하나로 NOT NULL인 `subscriptionEndDate`를 어떻게 채울지, (c) IPO와 유상증자 구분 기준(DS001 `stock_code` 유무는 가설), (d) OpenDART 일일 호출 한도, (e) 상태 코드 011/012 의미. 이후 E17 SF-2(ustockplus 보완)·SF-3 진행. Epic 19 SF-2~4는 E17 완료와 연동, Epic 18은 KIS 공식 GitHub 원문 열람(게이트 4건) 후 spec부터.
2. **접근성 재스캔 완료(2026-09-30)**: #195~#197 병합 후 12개 화면을 axe로 재스캔해 위반 0건(이전 32건). 리포트는 `docs/qa/accessibility-report.md`에 갱신됐다. 스캔은 `tools/accessibility-scan/run-scan.sh`(실행마다 빈 포트를 새로 골라 다른 프로젝트와 충돌하지 않음)로 돌린다. 참고: 스캔 스크립트는 로그인 실패 시 가입 계정을 만들므로 dev DB에 `testscan*@example.com` 잔여 계정이 쌓인다.
3. **checkstyle 남은 532건**: Javadoc 누락(MissingJavadocMethodCheck 260, MissingJavadocTypeCheck 150 등) 위주로, 자동 포맷으로 해결 안 되고 사람이 실제로 문서를 써야 한다. 우선순위 판단 필요.
4. **E2E 시나리오 실측 — 실측 완료(2026-09-30, #222), 결함 수정이 남음**: 시나리오 1~6을 실제 브라우저로 돌려 3 PASS / 3 FAIL. 실패 3건(E2E-2 로그인 불가, E2E-4 메시지 마침표, E2E-6 400 대 404)은 제품 결함이며 사용자 결정 후 별도 SF로 고친다. 시나리오 7~10(접근성·반응형·세션 쿠키·하이픈)은 범위에서 뺐다. 실행: `tools/e2e`에서 `pnpm run e2e`(사전 조건은 `docs/qa/e2e-report.md`).
4-1. **`ui/` format:check 9개 파일 실패**: 해결(2026-09-30, 포맷만 변경).
5. **팀장(Lead) 계층 재사용 여부**: 계속 미사용 — 이번 세션도 6.0 단독으로 9개 PR + 대형 스코프 확장 문서화를 무리 없이 처리했다.

## 워크스페이스 상태 (2026-09-30 세션 종료 시점)

- `backend-4`: 마지막 브랜치 `winter0123/checkstyle-test-cleanup`(병합 완료, PR #202, 현재 `origin/main` detached). 다음 세션은 `origin/main`에서 새 브랜치로 시작.
- `frontend-4`: 마지막 브랜치 `winter0123/e19-sf2-ipo-calendar`(병합 완료, PR #201, `origin/main` detached).
- `planning-5-2`: E17 spec 작업 워크스페이스(PR #191 병합 완료). 경로는 `orca worktree list --json`으로 재확인.
- 이번 세션의 워커 터미널은 모두 닫았고 진행 중인 워커 dispatch는 없다. 로컬 dev Postgres(`vibe-ai-team-postgres`, localhost:5433)는 기동 중이며 공유 DB라 테스트를 병렬 실행하면 안 된다. 테스트가 남긴 잔여 행이 다른 테스트를 깨뜨릴 수 있으니 이상하면 먼저 DB 잔여 행을 확인한다.

## 2026-09-30 세션 요약 (다음 세션 인계용)

**병합**: #200(E17 SF-4 일일 스케줄러+재시도), #201(E19 SF-2 공모주 일정 달력), #202(테스트 코드 checkstyle 위반 87건 정리). 상세 트러블슈팅은 `docs/daily-log/2026_09_29.md`.

**문서 정리(커밋 `8244941`)**: Epic 18 브레이크다운의 게이트 4건 조사 결과 반영(① 수수료·세금은 기간별매매손익현황 TTTC8715R만 있음/일자×종목 집계, ② "조회 전용"은 읽기전용 키가 아니라 쓰기 코드를 안 만든다는 뜻으로 확정, ③ 토큰은 만료시각까지 암호화 캐시, ④ 계좌번호는 CANO+ACNT_PRDT_CD 쿼리 파라미터). SF-5(`GET /ipo-schedule`)는 폐기하고 기존 `GET /ipos`를 씀 — 관련 문서(`docs/breakdown/ipo-schedule-sync.md`, `docs/spec/ipo-schedule-sync.md`, `docs/breakdown/posthoc-ux-redesign.md`, `docs/spec/posthoc-ux-redesign.md`, 로드맵) 전부 갱신.

**checkstyle 재조사로 정정**: 이전 세션 기록의 "532건"은 정확했다(이번 세션 초반에 `--rerun`과 `--rerun-tasks`를 헷갈려 캐시된 결과를 "87건"으로 잘못 판단했던 적이 있음 — **checkstyle 재검증은 반드시 `--rerun-tasks`를 쓴다, `--rerun`은 이 프로젝트에서 무시된다**). 실제 내역: 테스트 코드 87건은 이번 세션에 직접 수정해 PR #202로 병합 완료(SummaryJavadoc 마침표, star import 전개, 라인 길이, 중괄호, 그리고 테스트 전용 관례를 인정하는 `config/checkstyle/checkstyle-suppressions.xml` 신설). **남은 것은 프로덕션 코드 약 440건(`MissingJavadocMethod` 276 + `MissingJavadocType` 152 + 기타)** — 사용자에게 규칙을 끌지 직접 채울지 물었고, **"규칙 끄지 말고 네가 직접 수정해"로 확정**. 다음 세션에서 이어서 진행: `backend-4`에서 `DB_PASSWORD=vibe_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`로 최신 위반 목록을 뽑고, Master(코디네이터)가 직접 public 클래스·메서드에 한 줄 Javadoc을 채운다(기계적 작업이라 워커에게 맡기기엔 방향 판단 여지가 적지만, 분량이 크면 워커 위임도 재고).

**Epic 18 사용자 결정 3건 — 2026-09-30 확정**:
1. 매도·수수료·세금 자동 채움은 `TTTC8715R` 기준, 같은 날 같은 종목 분할매도는 합산해 매칭(Master 권고안 채택).
2. 실계좌 KIS 조회 1회 승인. 앱키는 사용자가 `.env`에 넣기로 했으나 아직 발급 전 — SF-3 완료 뒤 Master가 1회만 호출한다(키는 로그·커밋에 남기지 않는다).
3. E17 SF-2(ustockplus)는 폐기하지 않고 구현한다. 게이트 통과: `Origin`·`Referer`에 더해 `x-app-device: WEB` 헤더가 필수이고 조회 기간은 최대 89일이다.

**세션 후반 진행(2026-09-30 오후)**: checkstyle 프로덕션 528건은 #203으로 병합 완료(0건). E18 SF-1(V14/V15, 증권사 API 컬럼+시드)은 backend-4 워커 결과를 검증 중이고, E17 SF-2(ustockplus 클라이언트)는 backend-6 워커가 구현 중이다. **SF-1은 병합 전이라 공유 dev DB에 V14/V15가 이미 적용돼 있다** — 그 사이 다른 작업공간에서 전체 테스트를 돌리면 Flyway 검증이 실패하므로, E18 SF-2(V16)는 SF-1 병합 뒤에 main 기준으로 시작한다. 워커 정지 사례: Laguna S 2.1이 spec을 받고 17분간 파일 변경 없이 멈춰 nemotron-3-super로 교체.

**환경**: 세션 도중 Docker Desktop이 꺼져 있어 로컬 Postgres 컨테이너가 내려간 채로 테스트가 427건 실패한 적이 있었다(코드 문제 아님) — `open -a Docker` → `docker compose up -d`로 복구. 다음 세션 시작 시 `docker ps`로 컨테이너 상태부터 확인.

## 모니터링·운영 방식 (2026-09-29 갱신)

- **`check --wait`의 `--timeout-ms`는 300000(5분)으로 건다(2026-09-29 세션 중 15분→5분으로 정정).** 이유: watchdog(2분 주기)이 STALL/ERROR를 감지해도 그 감지 자체는 Master를 깨우는 신호가 아니다 — Master는 `check --wait`가 실제 `worker_done`/질문/에스컬레이션을 받아야만 깨어난다. 15분(또는 그 이상)으로 걸면 워커가 멈췄는데도 아무 메시지가 없는 상태에서 Master가 최대 그 시간만큼 못 알아챌 수 있다(2026-09-29 세션에 사용자가 먼저 발견하고 지적할 때까지 5분 이상 방치된 사례 실측). 5분 타임아웃으로 깨어날 때마다 `cat /tmp/orca-worker-status.txt`로 watchdog 요약을 직접 확인하는 습관을 매 wait 종료 시점에 넣는다(타임아웃으로 빈손 반환이어도 확인한다). 토큰 절약(긴 타임아웃) vs 감지 속도(짧은 타임아웃)의 트레이드오프이며, 5분은 사용자와 상의해 정한 값이다 — 더 짧게 걸면 재시도 턴이 늘어 토큰 낭비가 재발한다.
- **Orca 앱이 예고 없이 재시작/크래시될 수 있다.** `runtime_unavailable` 반환 시: `orca open` → `graph.state == ready` 폴링 → `run-use`로 재바인딩 → `worker-show`로 dispatch 생존 확인(추측으로 abandon/retry 금지).
- **재시작 직후 `check --wait`가 이미 처리된 오래된 메시지를 계속 재전송하는 현상이 반복 관측됐다.** `--ack <오래된 delivery_id>`가 `consumer_fenced`로 실패하면, 그 delivery는 이전 세대에 속한다는 뜻이다 — 재시도하지 말고 `orca orchestration check --run <run_id> --json`(wait 없이)으로 **현재 세대의 새 delivery_id를 다시 받아** 그것으로 ack한다.
- 워커 무응답을 watchdog이 "OK"로 잘못 판정할 수 있다 — 이번 세션에 실제로 있었다(사용자가 직접 발견). watchdog의 `ERROR_PATTERNS`는 이번에 보강했지만(PR #185), 앞으로도 새로운 오류 문구가 나타나면 그때그때 추가해야 하는 한계가 있다. **watchdog "OK"를 맹신하지 말고, 뭔가 이상하면 `orca terminal read`로 직접 확인한다.**
- OpenRouter 일일 무료 한도 소진 시 워커 전체를 OpenCode Zen(`opencode/<모델>`)으로 전환한다. Zen 경로도 내부적으로 특정 OpenRouter 모델로 폴백되며 동일하게 막히는 사례가 있었다 — 다른 Zen 모델로 재교체.
