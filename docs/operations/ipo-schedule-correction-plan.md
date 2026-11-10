# 운영 보정 실행 계획: 공모주 일정 정정·철회 (실행 안 함, 문서만)

> 상태: 운영 쓰기·배포 승인은 아직 받지 않았음. 근거 확인은 승인 대기 사안이 아니며 완료.
> V43 마이그레이션·코드는 아직 운영 배포되지 않았고, SimwonAmericaCorp.는 ACTIVE 상태다.

## 1. 운영 공개 API 집계 (2026-11-10)

공개 GET(`/api/ipos`) 응답만으로 프로그램 집계한 결과. 37건:

| 구분 | 건수 |
|------|-----|
| listingDate < 오늘(2026-11-10) | 11 |
| listingDate == 오늘 | 1 (동원파츠, id=29) |
| listingDate > 오늘 | 6 |
| listingDate null | 19 |

원 API는 syncSource/deletedAt/withdrawnReceiptNo를 반환하지 않으므로 해당 값은 미확인(추측 금지). 실DB 접속이 없으므로 "운영DB 완전 일치"라고 쓰지 않는다. 나머지 36건은 공식 일정 대조를 실시하지 않아 추가 보정 근거가 없다.

## 2. SimwonAmericaCorp. (id=20) — 유일한 철회 대상 후보

근거(OpenDART 공식 조회, Master 확인):

- corpCode 02038432 / 원공시 rcept_no=20261012000149, report_nm=증권신고서(지분증권), rm=철
- 철회신고서 rcept_no=20261030000200, rm=빈값 (DS001 지분증권 필터에서 제외됨)
- DS006 일반사항: 원공시 1건, sbd=2026년11월05일, pymd=2026년11월09일

실행 후 기대 상태:

- status=INACTIVE, deletedAt=실행 시점, withdrawnReceiptNo=20261012000149(원공시 접수번호) 저장
- 투자 이력(청약·배정·매도) 보존, listingDate=null 유지
- GET 목록에서 제외, 같은 receipt 재수집 차단, 다른 receipt 재추진 허용

## 3. 실행 절차 (승인 후)

1. V43 배포(last_imported_listing_date, withdrawn_receipt_no 추가) + 코드 배포
2. 관리자 동기화 또는 04:30 KST 배치 실행 — DS001에서 원공시(rm=철) 재조회 후 withdraw 조건(ACTIVE / syncSource!=null / subscriptionStartDate 일치) 충족 시 자동 철회 처리
3. GET 응답에서 id=20 제외 확인
4. 반복 실행 시 id=20이 재생성되지 않음 확인(SKIPPED_WITHDRAWN)

직접 SQL로 수동 UPDATE하지 않는다. 기존 예제의 camelCase SQL은 컬럼명과 불일치하므로 폐기한다.

## 4. 확인 한계

- 운영 적용 전까지 status/deletedAt/withdrawnReceiptNo의 현재값은 미확인
- 나머지 36건은 추가 보정 근거가 없으며, 보호 로직(추적값 규칙)으로 자동 정정만 차단된 상태
- 운영 쓰기 및 배포는 사용자 승인 후에만 실행한다
