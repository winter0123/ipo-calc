# 프로젝트 현재 상황 (Master 소유)

갱신: 2026-11-10.

## CI 실패·의존성 보완 (#496)

- 사용자 지시 "워커 돌리지말고 너가해"에 따라 이번 수정은 Codex가 직접 수행했다. 새 워커·Codex 하위 세션·watchdog은 만들지 않았다.
- 브랜치 winter0123/ci-quality-repair에서 로컬 구현·검증 완료, 원격 CI 확인 후 커밋을 main에 통합한다. 기존 .playwright-cli/ 산출물은 보존한다.
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
| 원격 CI | FAIL | 1차 run42720547292 backend PASS, UI의 테스트·정적 검사 PASS 후 정적 이미지 절대 링크에서 build FAIL; 상대 링크 수정 후 재검증 |
| 운영 DB·배포 | NOT_RUN | 이번 범위 밖 |

근거: docs/work-log/ci-quality-repair.md, /tmp/ci-repair-* 로그.

## 이미 병합된 계좌 보유 등록

- #493 CLOSED, PR #495(backend)·#494(UI) MERGED. main의 이전 정산 커밋 d5725289.
- 증권사 체크로 계좌 보유를 등록하고 번호는 NULL로 저장한다. 나중에 같은 ID에 최초1회 번호를 입력하며 기존 번호 교체는 제공하지 않는다.
- 동시 등록·후입력 보호, 청약 이력 계좌 삭제 거부, 번호 없는 브로커 연결·동기화 차단을 유지한다. 기존 이미지 자동 입력은 유지했다.
- V42는 계좌번호 NOT NULL 제약만 제거한다. 기존 암호문·ID·참조를 보존하며 운영 마이그레이션은 실행하지 않았다.
- 이전 Run run_374c2a862662 워커·임시 작업공간·watchdog은 정리했다. 이번 보완에는 워커를 사용하지 않는다.

## 다음 단계

1. #496 보완의 원격 CI UI/backend 성공 확인 후 main 병합·최종 정산.
2. 별도 배포 요청 시 backend V42와 UI를 함께 적용한다. 앞선 UI 배포는 SSH 인증 실패로 완료를 확인하지 못했다.
3. SimwonAmericaCorp. 철회·일정 정정은 연구 문서만 fc61626b로 병합했다. 공식 공모 건 식별·데이터 처리 결정 후 설계·구현 범위를 확정한다.

## 원격 빌드 경로 보완

- PR #497에서 backend는 PASS. UI 빌드가 Mac 전용 ui/public/images 절대 링크로 실패해 ../../images 상대 링크로 수정했다. 이미지 내용은 유지했다.
- 별도 임시 경로에 Git index를 아카이브한 새 소스로 offline frozen install/build를 실행하고 dist 아이콘·증권사 로고 포함까지 PASS 확인했다. 이번 수정도 Codex가 직접 수행했다. 새 CI 성공 확인 전 병합하지 않는다.
