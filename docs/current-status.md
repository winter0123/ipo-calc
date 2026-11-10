# 프로젝트 현재 상황 (Master 소유)

갱신: 2026-11-10.

## CI 실패·의존성 보완 완료 (#496)

- 사용자 지시 "워커 돌리지말고 너가해"에 따라 이번 수정은 Codex가 직접 수행했다. 새 워커·Codex 하위 세션·watchdog은 만들지 않았다.
- PR #497을 원격 CI 성공 확인 후 main에 병합했다(e6b89a7d). #496 CLOSED. 수정 커밋81cb42a1/f63c7d1b 및 병합 내용을 push했고 로컬 main을 동기화했다. 기존 .playwright-cli/ 산출물은 보존한다.
- backend는 Clock spy에 나노초 시각을 넣어 기존 CI 실패2건을 재현했다. 생성 응답 .123456789와 DB 왕복 .123457을 잘못 비교하던 테스트를 DB 저장값 기준으로 고쳤다. 시계를1초 진행하고 응답·DB 양쪽의 createdAt/updatedAt 불변을 정확히 확인한다. 제품 backend 로직은 수정하지 않았다.
- UI 투자자 전환 버튼에 aria-label을 명시해 이름의 공백 불일치를 해결했다. 기존 포맷 미준수6파일은 Prettier로만 정리했다.
- jsdom의 undici8.12.4와 typescript-estree 경로에 한정한 minimatch11.0.5로 갱신했다. 부모가 minimatch10.x만 허용해 좁은 override가 필요하며, 기존 minimatch3.x 경로와 직접 런타임 의존성은 유지한다.

| 검증 | 결과 | 근거 |
|---|---|---|
| backend 실패 재현 | PASS | 나노초 Clock으로 기존 두 실패를 로컬에서 재현 |
| backend 전체 테스트 | PASS | 슬롯4, 1,138건, failures/errors/skipped0 |
| backend Checkstyle/Spotless/build | PASS | main/test 위반0, 최종 명령 실행 |
| UI 전체 테스트 | PASS | 107파일876건, 기존2실패 해소 |
| UI lint/typecheck/format/build | PASS | 기존 lint 경고4·NotFoundPage import 경고는 유지, 오류0 |
| 의존성 audit/frozen install | PASS | 알려진 취약점9→0, lockfile 일치 |
| 전이 의존성 호환성 | PASS | 실제 설치 경로의 glob 양성·음성·숨김 파일 및 loopback HTTP 확인 |
| 원격 CI | PASS | run42721712150에서 UI quality·Backend quality 모두 SUCCESS, 검증된 f63c7d1b를 병합 |
| 운영 배포 | PASS | 2026-11-10 backend/UI 배포, 홈페이지·API HTTP 200 |
| Flyway 적용 버전 직접 조회 | NOT_RUN | 배포 후 DB 확인에 추가 sudo 인증이 필요해 수행하지 않음 |

근거: docs/work-log/ci-quality-repair.md, /tmp/ci-repair-* 로그.

## 이미 병합된 계좌 보유 등록

- #493 CLOSED, PR #495(backend)·#494(UI) MERGED. main의 이전 정산 커밋 d5725289.
- 증권사 체크로 계좌 보유를 등록하고 번호는 NULL로 저장한다. 나중에 같은 ID에 최초1회 번호를 입력하며 기존 번호 교체는 제공하지 않는다.
- 동시 등록·후입력 보호, 청약 이력 계좌 삭제 거부, 번호 없는 브로커 연결·동기화 차단을 유지한다. 기존 이미지 자동 입력은 유지했다.
- V42는 계좌번호 NOT NULL 제약만 제거한다. 기존 암호문·ID·참조를 보존한다. 2026-11-10 backend/UI 배포 후 backend 서비스 active 및 API HTTP 200을 확인했으며, Flyway 적용 버전은 DB에서 직접 조회하지 않았다.
- 이전 Run run_374c2a862662 워커·임시 작업공간·watchdog은 정리했다. 이번 보완에는 워커를 사용하지 않는다.

## 다음 단계

1. #496·PR #497 보완 완료. 활성 워커0이며 추가 검증 대기는 없다.
2. 로컬 보류 브랜치 `winter0123/listing-open-price-backend`와 `winter0123/sync-range-ui`는 이번 릴리스에 포함하지 않았다. Epic 21 백엔드 및 선행 백엔드가 필요한 UI 변경으로 별도 검토가 필요하다.
3. SimwonAmericaCorp. 철회·일정 정정은 연구 문서만 fc61626b로 병합했다. 공식 공모 건 식별·데이터 처리 결정 후 설계·구현 범위를 확정한다.

## 원격 빌드 경로 보완

- PR #497의 첫 CI는 backend PASS, UI build FAIL이었다. Mac 전용 ui/public/images 절대 링크를 ../../images 상대 링크로 수정한 뒤 두 번째 CI의 UI/backend 모두 PASS를 확인했다. 이미지 내용은 유지했다.
- 별도 임시 경로에 Git index를 아카이브한 새 소스로 offline frozen install/build를 실행하고 dist 아이콘·증권사 로고 포함까지 PASS 확인했다. 직접 수정·검증 후 CI 성공을 확인해 병합했으며, main의 backend/ui가 검증된 커밋과 동일함도 확인했다.

## 2026-11-10 UI 요청 묶음

- 청약 상태·일일 갱신, IBK 표시, 계좌 선택·관리 행, 상세 제목·홈 이동 반영.
- 검증 PASS: 관련 테스트 144개, lint/typecheck/build, diff 검사, 의존성 audit.
- 사용자 승인으로 커밋·PR 병합 및 UI 배포 진행.

## 2026-11-10 상장 상태·일정 정정 보완(로컬)

- PR #499 병합 및 UI 배포 PASS: Ansible ok=19 changed=1 failed=0, 웹/API 응답 PASS.
- 한국 날짜 기준 상장일 당일부터 상장 완료, 날짜 누락은 상장일 미정. 한국 자정·화면 복귀 시 일정 쿼리 재조회.
- 기존 04:30 KST 배치에서 보조 일정의 마지막 반영 날짜를 기록하고 이후 정정을 반영. 출처 불명인 기존 값과 관리자 입력은 보호. 상장일 정정 시 기존 시초가는 재수집하도록 비움.
- 철회 비고는 DS001/DS006 접수번호와 활성 자동 공모의 시작일 일치 시 논리 비활성화. 동일 철회 접수번호의 재생성 방지, 다른 접수번호의 신규 공모는 허용. 매칭 실패는 실패 목록에 기록.
- V43 마이그레이션 추가. 운영 적용 NOT_RUN. 기존 날짜 일괄 보정 및 Simwon 운영 철회 처리 BLOCKED: 입력 출처·공모 접수번호 실응답 확인 필요.
- 검증 PASS: UI 관련 36건, typecheck/lint(기존 경고 4건)/build, backend slot4 관련 49건/checkstyleMain/checkstyleTest/bootJar, diff 검사. 전체 회귀·실제 외부 API 연동 NOT_RUN.
- 이번 보완은 미커밋·미배포 상태. 워커 사용 없음.

## Orca 재시작 후 잔여 보완 워커

- Run run_c17799ef7190 재바인딩. 기존 ctx_2cdbd3ca6c67은 재시작 중 agent_readiness/terminal_handle_stale로 실패, 코드 변경 없음.
- 동일 Task task_592e55cfe896을 ctx_3c8be70ec51a로 재연결 중. OpenCode mini 2.1.3, OpenRouter Nemotron 3 Ultra (free), terminal term_1bb0cd01-2986-4345-8f4f-84fde11caade.
- 단일 작업공간에서 기존 미커밋 변경 보존. 워커 슬롯1, Master 검증 슬롯4. 운영 데이터 쓰기·배포는 워커 범위 밖.
- watchdog PID 83196, 동일 Run/코디네이터 전달 정상. CHILD_SCAN의 OpenCode DB 경로 확인은 UNKNOWN이며 Dispatch와 실제 모델 확인으로 대조.

- 재시도 ctx_3c8be70ec51a도 mini TUI 준비 감지 timeout. Orca 관리 실행 경로로 동일 Task를 재시도한 ctx_6ab42fcd26a9는 ready 및 지시 전달 확인. 실제 terminal term_387d46e4-41ea-4789-8c8d-04c62cd786ac, 지정 모델 OpenRouter nvidia/nemotron-3-ultra-550b-a55b:free, watchdog 모델 대조 확인. 자동 완료 알림 대기. 기존 mini 터미널은 작업 지시 없는 입력 대기로 보존.

- ctx_6ab42fcd26a9 완료 보고 후 Master 슬롯4 관련 test/checkstyle/bootJar PASS. 문서 집계 모순 및 .env-local 기존 OpenDART 키 확인 누락으로 보고 보정 요청.
- 후속 ctx_20a6b7c5aa6f / task_53bfd12b9857 같은 무료 워커에 배정. 기존 키 비노출 방식으로 실제 DS001/DS006 조회 및 문서/필요 코드 보완. GT-02 기술검증은 사용자 승인 불필요, 실제 Orca Gate 0개. 운영 쓰기·배포는 미실행.

- Master 공식 OpenDART DS001/DS006 직접 GET PASS(기존 로컬 키 비노출). Simwon corp02038432, 원공시20261012000149 rm=철, 철회신고서20261030000200. DS006 원공시 일반사항 sbd2026년11월05일/pymd11월09일 확인.
- 워커의 디듀프 원인 결론 반려: 지분증권 필터가 철회신고서를 먼저 제외하므로 원공시가 최신 철회신고서에 밀려 탈락하지 않음. DS006은 corp/date 조회이며 접수번호별 조회 주장도 반려.
- 후속 ctx_c1a9162b4f13/task_dc9ffe6d6e54 배정: 실제 응답 회귀,동일 철회 반복 처리 idempotence,필요시 동일일자 접수번호 정렬 보완,문서 오류 제거. 운영 쓰기·배포·커밋 미실행.

- ctx_c1a9162b4f13 검증: Master 슬롯4 관련4클래스/checkstyle/bootJar PASS. 그러나 신규 receipt 선택분기 회귀·철회 멱등 보완 누락 및 운영표 허구가 남아 승인 반려.
- 후속 ctx_77e7f27f3394/task_8299bc64bd1c: 누락된 실제 응답·멱등 테스트와 최소 코드, API JSON 기반 운영표 재생성, 문서6종 축소/정정 요청. 실 DS006은 원공시1건, 기존 배포 rm미처리가 확정 원인임. 최신상세 혼합선택은 잠재위험으로만 기록하도록 지시.

- ctx_77e7f27f3394 코드 보완 Master 검증 PASS: 슬롯4 관련4클래스77건,checkstyleMain/Test,bootJar. 멱등·원공시선택·실sbd파싱회귀 확인. 운영문서는여전히964줄과오류남아별도정정.
- 기존워커release완료. 무료probe OK인OpenRouter cohere/north-mini-code:free 문서전담 ctx_fb88f9734edd/task_4f419fdee442 배정(terminal term_bcb7c5dc-d0fc-4b8d-a0cd-d3b9e58a26b4). 문서6종실제덮어쓰기/간결화/사실정정만,코드수정금지.
- 운영방안은직접SQL아닌배포후기존동기화로Simwon1건제외. 운영V43/코드배포미실행,승인별도필요.

- North Mini 문서워커는제약미충족/허위완료및보고누락2회로ctx_fb88f9734edd abandon(배정fence). release는identity_unproven으로실프로세스보존,worker-show live/idle 확인.
- 동일Task task_4f419fdee442를ctx_96ed4acf4446로재시도. OpenRouter thinkingmachines/inkling-2-small:free probe OK,정상배정. 문서6개만,코드변경금지. terminal term_157f16ff-a930-40f0-9fa9-e147302b4bb0.

## 잔여 수정 검증 완료 / 운영 적용 승인 대기

- 문서6종 실측37/40/55/44/32/46줄, 사실·운영안 직접 검토 및 diff 검사 PASS. 코드검증은 Master 슬롯4 관련77건/checkstyle/bootJar PASS, UI36건/typecheck/lint/build PASS.
- ctx_96ed4acf4446 release완료(terminal닫힘). NorthMini abandoned터미널은identity_unproven으로보존(이전관측idle). mini실행실패터미널도입력대기로보존. 새워커없음,watchdog중지.
- 운영적용 task_84d2b61d1967에Gate생성. V43포함backend/UI배포후기존동기화로Simwon(id20/corp02038432/원공시20261012000149)논리비활성화,투자이력보존/목록제외/재생성없음확인계획. 직접SQL없음.
- 코드·문서 미커밋,운영데이터변경/배포NOT_RUN. 기타36건공식일정추가대조NOT_RUN.

## 운영 적용 승인 반영

- 사용자 '진행해'로gate_130bb69219e6해결. 커밋·푸시·CI·병합후V43포함backend/UI배포와기존동기화로Simwon철회처리진행.
- 브랜치 winter0123/ipo-schedule-reconciliation. 최종검증PASS:backend77건/checkstyle/bootJar,UI36건/typecheck/lint/build,의존성audit,git diff검사.

## 자동 재점검 확대 구현 착수

- PR500은9c3b1b2b로병합,CI양쪽PASS,Ansible27/changed3/failed0으로backend/UI배포완료. 현재Simwon id20 ACTIVE. 관리자동기화요청은사용자지시로철회하고관리자조작없는자동배치확대로전환.
- 사용자상세계획구현승인. branch winter0123/automatic-ipo-reconciliation,Run run_b10d1515cf68. DAG SF0 task_2fd4a7ff6dc7→SF1 task_207fc1a6746b(실제deps설정).
- SF0 dispatch ctx_4ddbf2072f1e,terminal term_0194f2d5-7f8b-487b-8bf8-6e7ff7200e09,OpenCode/OpenRouter thinkingmachines/inkling-2-small:free probe/화면확인. 활성작업공간1개라충돌방지순차.
- SF0 공식API기간·식별·지속상태계약확정후Master검토,SF1자동철회/일정정정/배포후복구실구현. 워커DBslot1,Master검증slot4. 기존V43수정금지,V44이후사용. 운영쓰기·배포는워커범위밖.
- watchdog PID38237 동일Run에재시작. 시작초기UNKNOWN은ready터미널확인후대조.

- SF0검토완료: 계약오류(DS001페이지2회상한/DAILY유니크/앱버전fallback/청약·환불보호누락/receipt저장불가주장)보정지침전달. 완료task의deps해제확인후SF1배정.
- SF1 task_207fc1a6746b / ctx_8886cb3dbfd5 / terminal term_d3ead477-4533-4b14-a938-862c86bef150. OpenRouter Inkling2Small(free)화면확인,DBslot1. 문서/필수이력조회서비스보완범위추가허용.
- 복구키ipo-reconciliation-v1,POST_DEPLOY에만SUCCESS유니크,매일04:30재점검/페이지전체조회/자동날짜원본추적및수동보호구현. 사용자구현승인재질문불필요.

- SF1 Master 슬롯4 전체검증 --rerun-tasks PASS(1m58s). 단 코드검토6건으로승인반려: 재점검예외빈정상결과→복구허위SUCCESS,다른공모receipt업서트덮어쓰기,회사별Runtime격리누락,빈응답성공판정,disabled스케줄에서도기동복구,트랜잭션안유니크예외삼키기.
- 동일워커에실제코드/회귀테스트보완요청. 아직미커밋·미병합·미배포. 공유개발DB COMMENT probe는지시위반으로기록,slot1외DB접근금지재지시.

## 자동 재점검 구현·Master 검증 완료(로컬)

- SF1후속ctx_cdd6223a1f92 보완6건수용. Master 슬롯4 reset후전체검증 --rerun-tasks PASS:1189건/실패0/오류0,checkstyleMain/Test,spotlessJavaCheck,bootJar(2m2s),git diff검사.
- 예외/빈응답의복구허위SUCCESS차단,회사별Runtime격리,다른공모판단불가시전체값보존,disabled조건존중,ONCONFLICT원자기록확인.
- V44원공시receipt/날짜원본추적/실행이력추가. 배포후ApplicationReady비동기복구→한국04:30배치정기전수점검. 복구키ipo-reconciliation-v1,실패는미완료로재시도,수동로그인불필요.
- 식별불명·공시자료없는자동공모는보류/FAILED로남을수있음. 기존출처불명날짜의일괄백필없음. 다중서버배타제어는범위밖(현재단일JVM).
- 워커release완료,watchdog중지. branch winter0123/automatic-ipo-reconciliation. 이번V44자동화는미커밋·미배포,운영자동복구실행검증NOT_RUN. 이전PR500배포버전과구분.
