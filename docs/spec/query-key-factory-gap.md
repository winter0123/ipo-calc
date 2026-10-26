# Query key factory 누락 Spec

## Acceptance Criteria

1. 공지사항 public list/detail, popup, admin list key는 entity factory에서 생성한다.
2. IPO sync status key는 `ipoKeys` factory에서 생성한다.
3. 전체 투자내역 key suffix도 `investmentHistoryKeys`가 생성하며 목록 key의 하위 prefix를 유지한다.
4. 생성되는 실제 배열 값, query function, AbortSignal 전달 및 mutation invalidation 범위는 기존과 같다.
5. factory의 개별 key 구조와 prefix를 테스트로 고정한다.

## 비범위

캐시 namespace 변경, stale/refetch 정책 변경, API 호출·응답 계약 변경.
