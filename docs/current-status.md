# 프로젝트 현재 상황 (Master 소유)

갱신: 2026-11-10. 기본 1차 개발 완료 후 사용자 기능 수정 단계다.

## 계좌 보유 체크 등록 완료

- GitHub #493 CLOSED. 백엔드 PR #495 MERGED(4b4c7a0d → bdd6375e), 프론트엔드 PR #494 MERGED(823633df → 5eba16f6). 사용자 승인에 따라 커밋·push·main 병합을 완료했다.
- 증권사별 체크로 계좌 보유를 등록한다. 계좌번호는 가짜 값 대신 NULL로 저장하고, 미입력 계좌는 나중에 같은 ID로 번호를 한 번 입력할 수 있다. 기존 번호 교체는 제공하지 않는다.
- 번호 없는 등록은 가장 오래된 활성 계좌를 재사용한다. 투자자 쓰기 락과 refresh로 동시 등록·번호 입력을 보호한다. 청약 이력이 있으면 취소·삭제 여부와 관계없이 계좌 해제를 거부한다.
- 번호가 없으면 증권사 조회 API 연결·갱신·동기화를 외부 호출 전에 차단한다. 기존 이미지 자동 입력은 유지했다.
- V42는 account_number의 NOT NULL 제약만 제거한다. 기존 암호문·ID·참조를 보존하며 운영 마이그레이션은 실행하지 않았다.

## Master 검증

| 검증 | 결과 | 근거와 범위 |
|---|---|---|
| 백엔드 전체 테스트 | PASS | 슬롯4, 1,138건, failures/errors/skipped 0 |
| 마지막 동시성 회귀 보완 | PASS | 계좌 Controller 34건, 동시 POST 모두201·계좌1개, PATCH 성공1/409 1 |
| Checkstyle·Spotless·build | PASS | main/test 위반0, 직접 명령 실행 |
| 프론트엔드 대상 회귀 | PASS | 관련7파일68건 |
| UI typecheck·lint·build | PASS | 기존 lint 경고4, 오류0; 마지막 CSS 수정 후 typecheck/build 재실행 |
| 변경 UI Prettier·diff check | PASS | 모든 변경 UI 및 main 통합 diff 확인 |
| 모의API 브라우저 | PASS | 체크 등록→번호 후입력, 키보드 포커스, 375/640/768/1024px 가로 넘침 없음 |
| 통합 소스 동일성 | PASS | main의 backend/ui가 직접 검증한 각 브랜치와 동일 |
| 전체 UI 테스트 | FAIL | 874 PASS, 기존 InvestorGate 2 FAIL; 변경 전에도 재현된 기준선 |
| 전체 UI format:check | FAIL | 변경 밖의 기존 AdminAnnouncementsPage 2파일·SecuritiesLogo 3파일·securitiesLogo.ts |
| 의존성 audit | FAIL | 기존 high4/moderate5, jsdom→undici 포함; 의존성 변경 없음 |
| 실제 백엔드 연결 브라우저 | NOT_RUN | 모의API 검증과 구분 |
| 원격 CI 최종 결과 판정 | NOT_RUN | PR 생성 후 실행 중인 상태까지만 확인, 로컬 PASS와 구분 |
| 운영 DB·배포 | NOT_RUN | 이번 승인 범위는 커밋·push·병합 |

검증 로그는 /tmp/account-presence-master-*에 보존했다. 이전 worker의 부정확한 테스트 개수·통과 주장 대신 위 직접 실행 결과를 기준으로 한다.

## Orca·작업공간 정리

- Run run_374c2a862662의 구현 및 보완 태스크 완료. 활성 워커 0, ready 큐 0.
- OpenRouter 일일 무료 한도 소진 후 단일 요청을 통과한 Zen Beam Free(backend)·Flint 40B Free(frontend)로 전환했다. 동일 모델 중복·유료 모델·하위 워커는 사용하지 않았다.
- 완료 Dispatch release와 PTY 종료를 확인했다. 두 임시 account-presence 작업공간의 남은 셸도 닫고 작업공간을 제거했다. watchdog PID98361 중지.
- main의 기존 .playwright-cli/ 산출물은 보존했다. docs/daily-log/2026_11_09.md와 2026_11_10.md에 정산을 남긴다.
- watchdog CHILD_SCAN의 OpenCode DB 경로 조회 실패는 설치 버전의 db path 미지원이다. 429 행 번호 오탐도 실제 화면으로 구분했으며 감시 코드 자체는 이번 범위에서 수정하지 않았다.

## 다른 요청과 다음 후보

1. 배포 요청 시 백엔드 V42와 UI를 함께 적용한다. 앞선 UI 배포는 SSH 인증 실패로 실제 완료를 확인하지 못했다.
2. SimwonAmericaCorp. 철회·일정 정정 문제는 연구 문서와 work-log를 fc61626b로 push했다. 철회 공시를 기존 공모 건과 연결해 상태를 전이하고 일정 변경을 반영하는 구현은 아직 하지 않았다. 공식 원문·공모 건 식별 및 데이터 처리 결정을 확정한 뒤 spec부터 진행한다.
3. 기존 InvestorGate 테스트·포맷·의존성 audit 부채는 별도 범위를 정해 처리한다. 이번 기능에 무관한 코드·의존성 수정은 하지 않았다.
