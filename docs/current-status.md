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

## PR501 운영 배포·자동 복구 확인 완료

- PR501 cdb392b4978c019fa6acb515fecfe5cbe6978fba 병합. UI/Backend quality 모두SUCCESS. main동기화완료.
- 사용자인증후backend배포및읽기전용운영검증PASS:Ansible ok11/changed2/failed0. 중간FAILED-RETRYING은복구기록생성대기조건재조회이며최종실패아님.
- 운영DB읽기검증:Flyway44 success=true,ipos id20 INACTIVE/withdrawn_receipt_no20261012000149,POST_DEPLOY_RECOVER SUCCESS/targets37/succeeded37/failed0.
- 관리자로그인·버튼조작없이ApplicationReady자동복구실행확인. 공개API HTTP200/목록36건/Simwon id20없음.
- 기존자동화구현전체1189건/checkstyle/spotless/bootJar PASS. 다음04:30실제일일배치·실서버재기동스킵실측NOT_RUN(로컬회귀검증PASS).
- 워커정리완료. 미추적.playwright-cli/와구형simwon-withdrawal-procedure초안은배포/커밋에서제외. 새제품수정없음.

## 2026-11-11 환불 기본필터 해제

- 사용자요청:모든화면에서공모주일정진입시환불기본선택off,청약/상장on. 명시URL/수동환불선택존중. parseVisibleTypes와toTypesParam의새기본왕복일치도수정.
- branch winter0123/calendar-refund-default-off. Run run_1fa8904e7107,task_f7073fb689bf,dispatch ctx_33729951d185,terminal term_5f739c8d-dda3-43c9-a046-4cde75b7f3ac. OpenRouter Inkling2Small무료모델응답/화면확인.
- frontend단일워커배정,제품변경3파일범위,완료후Master직접검증. 이번수정배포미실행.

- 환불기본off수정완료:parseVisibleTypes누락/무효→청약·상장,types빈값→전체off유지,명시환불선택존중,toTypesParam새기본null/all3명시로왕복일치.
- Master 직접검증PASS:관련4파일116건,typecheck,lint(기존경고4/오류0),build,diff검사. 워커release/watchdog중지. 이번UI수정미커밋·미배포,실브라우저수동검증NOT_RUN.

## 2026-11-11 통합 요청 확인

- 열린PR0개,활성worker0개. 기존브랜치중대부분main의ancestor. nonancestor중automatic-ipo-reconciliation/ipo-schedule-reconciliation/ui-request-batch는PR501/500/499 squash병합으로반영완료. listing-open-price-backend의핵심서비스는main에동일동작(포맷차이),sync-range-ui는main에기간선택및진행상태가구현되고스타일시스템만변경됨. 구버전재병합불필요.
- 미반영환불기본off UI3파일및Master상태/일일로그를현재브랜치에서커밋·PR·CI후병합한다. 임시.playwright-cli/및오래된운영SQL초안은포함하지않는다.

## 2026-11-11 배정 방식 제거 착수

- PR502 CI양쪽SUCCESS/병합261d61a8확인,main갱신후branch winter0123/remove-allocation-method생성.
- 사용자가DB allocation_method컬럼·기존값까지제거명시승인. 다른배정값/투자이력보존,운영배포는아직미실행.
- Run run_8a5f03c13533,DAG UI task_1f02ec7ec211→backend task_453c4a6a3d3e. UI워커ctx_eca00c5dd18a/terminal term_2aea129b-7a3c-4211-832f-72d67d3665e0,OpenCode/OpenRouter Inkling2Small무료모델probe및화면확인.
- UI등록·통합등록·수정·관련표/스키마/가이드제거중. 완료검토후backend에API/엔티티/서비스/adminDTO/기존참조제거및V45컬럼DROP마이그레이션배정. 워커slot1/Masterslot4.

- 사용자추가요청:매도등록청약선택제거,공모주/투자자/증권계좌로서버가자동연결. 기존SaleService에tuple조회존재,등록검증이기존배정찾기전allocatedQuantity요구하는공백함께보완. IPO일반진입선택/단일계좌기본/복수계좌만선택/기존배정재사용/미배정직접등록/소유권보호범위확정.
- 같은작업공간내배정방식API·DB제거와서비스수정충돌가능해후속Task를backend선행deps로순차정의. 먼저작업완료검증후이어배정,워커slot1/Masterslot4.

- 배정방식 UI Master 검증 PASS: 대상6파일64건/typecheck/lint(경고4·오류0)/build/git diff검사. 요청·응답스키마/등록·수정payload에서제거확인. UI워커release후backend task_453c4a6a3d3e 배정,DBslot1. 매도자동연결후속 task_ee1026768d14는 backend완료의존. 신규배포NOT_RUN.

- 추가요청 증권사관리: 체크박스→로고→회사명→계좌번호→버튼의실제DOM순서,등록·미등록목록PC2열/모바일1열,첫계좌헤더와한줄,작은버튼·여백/접근성유지. 같은단일작업공간의매도자동연결후속deps로UI개편Task정의. 검증/구현NOT_RUN.

- 최신요구 우선: 공모주청약 공통등록2버튼제거,공모주별진입만/등록IPO고정명칭/select제거,청약일실제기간날짜버튼. 후속UI Task작성. 중요: 매도 task_ee1026768d14 시작할때 기존spec의 일반진입IPO select 요구는폐기하고 최신요구(문맥없으면목록안내,공모주고정)를추가전달할것. 날짜선택은한국오늘기간내오늘/그외첫날,누락·역전이면차단,기존매도일달력보존. 구현·배포NOT_RUN.

- 배정방식 backend Master 슬롯4 reset후전체검증PASS:1189 tests/실패0/오류0/skipped0,checkstyleMain/Test,spotlessJavaCheck,bootJar(2m17s),diff검사. V45 allocation_method만DROP/나머지필드보존확인. 워커release.
- 매도자동연결 task_ee1026768d14/ctx_b84e5420ba01/term_7c503904-a059-4071-a9e9-f703ad6fcdba/OpenRouter Inkling2Small무료모델배정. 최신요구로일반IPOselect폐기·공모주고정·문맥없으면목록안내를dispatch에추가지시. 워커slot1.

- 매도자동연결 Master 검증PASS:slot4 전체1197건/실패0·오류0,checkstyle/spotless/bootJar,UI72건/typecheck/lint/build. 코드검토후UI조회대기·실패상태제출차단/URL문맥변경시낡은연결정리/캡처가다른계좌연결로전환하는문제3건보완요청. 후속증권사UI는보완검토후착수. 배포NOT_RUN.

- 매도3건보완ctx_53983314ba87 Master UI검증PASS:81건/typecheck/lint(기존경고4·오류0)/build/diff. 조회로딩·오류전송차단+버튼/submit방어,URL문맥초기화·허용계좌대조,다른계좌캡처자동전환금지확인. backend직전1197건검증유효(이후backend변경없음). 보완워커release후증권사관리 task_d436c4c19b15 배정.

- 증권사관리 Master검증PASS:29건/typecheck/lint/build. 화면검토에서PCgrid카드동일높이빈공간발견,align-items:start등최소보완요청. 일반실제증권사·번호있는단일계좌모바일/PC증적보강및760px제약주장정정요청. 마지막등록UI task_050ccc69bce1은보완후착수. 배포NOT_RUN.

- 증권사UI실증적재검토반려:375px일반미래에셋단일계좌3줄/IBK2줄,PC미래에셋2줄로한줄요청미충족. 기존설치lucide아이콘액션+accessible name/title,부가별칭title이동/자동가져오기아이콘화,단일행DOM정렬재지시. Master자체44px조건은최신한줄계약과충돌해32px수준(최소24px타겟·간격/키보드유지)허용으로수정. 다음Task는이후착수.

- 사용자최신요구:보기버튼삭제,계좌번호자체버튼클릭으로평문/마스킹토글. ctx_344461cb66f5에즉시전달. 미입력클릭은번호입력폼,중복번호입력액션제거,키보드/보안API/오류처리보존. 구현·검증대기.

- 사용자최신UI계약으로상시아이콘여러개요구폐기:평소계좌관리버튼1개→수정/삭제/계좌추가펼침. 미입력은텍스트이며수정으로최초번호입력. 번호클릭보기/숨김유지. ctx_344461cb66f5에전달,반응형/보안/체크등록보존. 구현검증대기·배포NOT_RUN.

- 사용자추가:증권사관리자동가져오기링크/아이콘/배지/연동CTA노출제거. 전용credential조회불필요분정리,범위SecuritiesPage관련파일까지확대허용. 매도등록자동입력·실제broker서비스보존. ctx_344461cb66f5전달.

- 증권사관리최종Master검증PASS:33건/typecheck/lint(기존4경고)/build/diff. 375/1280증적직접확인:단일계좌32px한줄/번호토글/계좌관리하나/PC2열모바일1열/자동가져오기·연동CTA제거. 복수계좌추가행허용,미입력은수정에서최초입력. 워커release후마지막task_050ccc69bce1배정. 새변경미커밋·배포NOT_RUN.

- 사용자지적한국투자증권계좌관리2개:현재계좌별토글구조때문. 최신계약증권사당정확히1토글로정정,복수계좌수정삭제는펼침안각계좌별·계좌추가는회사당1개. 현재등록UI worker가같은tree작업중이므로그후속deps로수정Task정의.

- 공모주문맥등록 Master 전체UI검증PASS:108파일948건/typecheck/lint(기존4warning)/build/diff. 공통등록2버튼제거/IPO고정·문맥없음안내/청약기간날짜버튼·한국날짜기본/미정·역전차단확인. 워커release후최종 task_5445509c261b(회사당계좌관리1개)배정. 제품미커밋·배포NOT_RUN.

## 2026-11-11 요청 묶음 최종 검증 완료(미배포)

- 배정방식 입력·표시·API·엔티티 제거 및 V45 allocations.allocation_method DROP 준비. 사용자 컬럼/기존값 삭제 승인 반영, 운영 마이그레이션 NOT_RUN. 배정수량·금액·날짜·투자이력 유지.
- 매도 청약 select 제거, 공모주·투자자·계좌 기반 서버 청약/배정 자동 연결. 기존 배정 재사용, 조회대기·오류 제출 차단, 다른 문맥 연결 차단. 번호 없는 계좌 허용.
- 등록은 공모주별 진입·이름 고정, 공통 등록2버튼/IPO select 제거. 문맥없는 URL은 목록 안내. 청약일 실제 기간 날짜 버튼·한국오늘/첫날 기본·기간미확인/역전 차단.
- 증권사관리 체크→로고→이름→번호→계좌관리. PC2열/모바일1열, 증권사당 계좌관리1개, 펼침 내 계좌별 수정삭제·회사계좌추가1개. 번호클릭 평문/마스킹, 미입력은 수정으로 최초입력. 자동가져오기/연동CTA 제거, 매도자동입력 유지.
- Master 검증 PASS: backend 슬롯4 전체1197건/실패0/오류0/skip0/checkstyleMain·Test/spotlessJavaCheck/bootJar(backend 이후 변경없음). 최종 UI108파일949건/typecheck/lint(기존4warning·오류0)/build/git diff검사 PASS.
- 실제 계정 통합·운영 배포 NOT_RUN. 모바일375/PC1280 증적 검토 PASS(모의 API). 좁은 복수계좌 카드의 긴 회사명은 줄임표와 title로 표시됨.
- 모든 워커 release·terminal 종료, watchdog 중지. branch winter0123/remove-allocation-method 미커밋. 사용자 배포 지시 전 배포하지 않음. .playwright-cli/ 및 구형 simwon-withdrawal-procedure.md 초안은 별도 보존·커밋제외.
