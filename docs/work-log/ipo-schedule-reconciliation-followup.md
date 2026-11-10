# 작업 로그: 공모주 일정 정정·철회 후속 (ipo-schedule-reconciliation-followup)

> 2026-11-10 정정 재작성판. 이전 본문은 사실 오류(원인 오진단·수치 오류·Gate 허구·운영DB 일치 표현)로 폐기.

## 2026-11-10

- 초기 조사(Nemotron 워커): 공개 API 37건 집계(당시 12/1/6/19로 잘못 기록, 이후 11/1/6/19로 정정), Simwon 공시 확인(원공시 20261012000149 rm=철, 철회신고서 20261030000200 rm 빈값).
- 코드 수정 워커: processCompany rm철 분기, withdraw 멱등, persist SKIPPED_WITHDRAWN, mergeSupplementalSchedule 보호(V43 미커밋), IpoService protectListingDate, 프런트 phase/재조회 수정.
- 본 정정 재작성(정정 워커): 문서 6종을 확인 사실 기준으로 처음부터 덮어씀. 코드·테스트·current-status·daily-log 미수정.

## Master 확인 사실 (Master 직접 검증)

- 관련 4클래스 77건 / checkstyleMain / checkstyleTest / bootJar PASS
- 프런트 36건 / typecheck / lint / build PASS
- DS001 지분증권 필터 선행 → 철회신고서 제외, 원공시 보존 (코드 확인)
- DS006 회사/기간조회 응답은 원공시 1건 (Simwon 실측)

## 정정된 사실

- 확정 원인: 배포 코드의 rm 미처리. (이전의 "DS006 최신 rceptNo(철회신고서) 선택이 원인" 주장은 Simwon 실응답과 불일치)
- 원공시 receipt 선택분기는 방어 보완일 뿐 Simwon 실응답 원인이 아님
- 집계: listingDate <오늘 11 / ==오늘 1 / >오늘 6 / null 19 = 37건
- '운영DB 완전 일치' 표현 금지. syncSource/deletedAt/withdrawnReceiptNo는 공개 API 미반환 → 미확인
- 나머지 36건 공식 대조 미실시 → 추가 보정 근거 미확인
- 시초가 수집은 protectListingDate를 호출하지 않음(updateListingOpenPrice만 호출)
- 관리자 listingDate 입력만 protectListingDate를 호출해 추적값을 null로 만든다
- 가상 Gate/SF/미실행 테스트 PASS 기록은 허구이므로 제거

## 남은 작업

- 운영 적용(배포·마이그레이션 후 id=20 철회): 사용자 승인 대기
- 나머지 36건 공식 일정 대조 필요 여부: 근거 없이는 보정 보류
