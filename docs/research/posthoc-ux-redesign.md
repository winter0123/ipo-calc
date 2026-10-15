# 사후 등록 UX/UI 전면 개선 연구 문서 (posthoc-ux-redesign)

> **Epic**: Epic 19 (사후 등록 UX/UI 전면 개선)
> **분야**: 기획 (Planning) — planning 워크스페이스
> **작성일**: 2026-09-29
> **관련 Epic**: Epic 17 (공모주 청약 일정 자동 동기화), Epic 18 (증권사 조회 전용 연동)
> **참고 문서**: 
>   - docs/roadmap.md 1.1절(2026-09-29 스코프 확장 결정)
>   - ui/src/App.tsx (현재 전체 라우트 목록)
>   - ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx, ui/src/features/investment-history/FilterBar/FilterBar.tsx
>   - ui/src/pages/DashboardPage/DashboardPage.tsx, ui/src/features/dashboard/
>   - ui/src/features/subscription-register/, ui/src/features/allocation-register/, ui/src/features/sale-register/, ui/src/features/cost-register/
>   - ui/src/pages/InvestorRegisterPage 계열, ui/src/pages/SecuritiesAccountRegisterPage 계열
>   - docs/qa/accessibility-report.md
>   - ui/src/entities/dashboard/, backend/src/main/java/com/ipocalc/dashboard/
>   - docs/breakdown/, docs/spec/, docs/architecture/ 폴더의 기존 Epic 문서 형식

## 1. 현재 사용성 문제 목록화

### 1.1 투자 내역 조회 화면의 주요 문제점
- **수동 ID 입력 필터**: 투자자 ID, 증권계좌 ID, IPO ID를 모두 텍스트 입력으로 요구 (ui/src/features/investment-history/FilterBar/FilterBar.tsx lines 22-70)
  - 투자자 ID: 투자자 선택 드롭다운이 아닌 직접 숫자 입력 필드
  - 증권계좌 ID: 증권계좌 선택 드롭다운이 아닌 직접 숫자 입력 필드  
  - IPO ID: 공모주 선택 드롭다운이 아닌 직접 숫자 입력 필드
- **연쇄적 정보 재입력 문제**: 투자자 → 증권계좌 → 청약 → 배정 → 매도로 이어지는 체인에서 각 단계에서 이전 선택 정보를 다시 처음부터 입력해야 함
- **필요 정보 부족**: 사용자가 기억해야 하는 여러 개의 숫자 ID를 지속적으로_lookup 해야 하는 인지 부하

### 1.2 등록 폼들의 현재 상태 (사실 확인)
다행히 대부분의 등록 폼은 이미 드롭다운 선택 방식을 채택하고 있음:
- **청약 등록**: 투자자, 증권계좌, 공모주를 드롭다운으로 선택 (ui/src/features/subscription-register/SubscriptionRegisterForm/SubscriptionRegisterForm.tsx lines 105-178)
- **배정 등록**: 특정 청약에 대한 배정이므로 투자자/증권계좌 선택 대신 해당 청약 정보를 참조 (ui/src/features/allocation-register/AllocationRegisterForm/AllocationRegisterForm.tsx)
- **매도 등록**: 투자자 선택 드롭다운 → 해당 투자자의 청약 목록 드롭다운 → 해당 청약의 배정 목록 드롭다운 (ui/src/features/sale-register/SaleRegisterForm/SaleRegisterForm.tsx lines 122-192)
- **비용 등록**: 청약 선택 드롭다운 (ui/src/features/cost-register/CostRegisterForm/CostRegisterForm.tsx lines 79-102)
- **투자자 등록**: 표시 이름 텍스트 입력 + 관계 드롭다운 (ui/src/features/investor-register/InvestorRegisterForm.tsx lines 62-106)
- **증권계좌 등록**: 증권사 선택 드롭다운 + 계좌번호 텍스트 입력 (자동 하이픈 삽입) (ui/src/features/securities-account-register/SecuritiesAccountRegisterForm.tsx lines 94-147)

### 1.3 접근성 문제 (docs/qa/accessibility-report.md 기반)
- **landmark-one-main 위반 28건**: 문서에 주요 `<main>` 랜드마크가 부재하여 스크린 리더 네비게이션 어려움
- **region 위반 28건**: 페이지 콘텐츠가 적절한 랜드마크(header, nav, main, footer)로 감싸지지 않음
- 이러한 문제는 대부분 공통 레이아웃 컴포넌트 lebel에서 발생 가능성 높음
- 투자 내역 조회 페이지의 심각한 문제: 색상 대비 위반 3건 (힌트 텍스트 색상 불충분)

## 2. 증권사 계좌 선택형 조회 화면 설계안

### 2.1 정보구조 (IA) 제안
```
투자 내역 조회 페이지 (/investments)
├── 헤더: "투자 내역" 제목
├── 계좌 선택 섹션
│   ├── 투자자 선택 드롭다운 (로그인 사용자의 투자자 목록)
│   └── 증권계좌 선택 드롭다운 (선택된 투자자의 증권계좌 목록)
├── 필터 섹션 (선택 사항)
│   ├── 공모주 선택 드롭다운 (모든 이용 가능한 IPO 목록)
│   └── 초기화 버튼
├── 투자 내역 결과 영역
│   ├── 로딩 상태
│   ├── 빈 상태 안내
│   └── 투자 내역 테이블 (페이지네이션 포함)
└── 네비게이션: 대시보드로 이동 링크
```

### 2.2 API 호환성 분석
- **현재 API**: `GET /investment-history?investorId=&securitiesAccountId=&ipoId&page=&size=` (이미 존재)
- **변경 필요성**: 프론트엔드만 재설계하면 됨 (백엔드 변경 불필요)
  - 현재 API는 이미 investorId, securitiesAccountId, ipoId 파라미터를 지원
  - 다만 현재는 숫자 ID를 직접 입력받지만, 드롭다운 선택값을 숫자 ID로 변환해서 전달하면 동일하게 작동
- **추가 고려사항**: 
  - 투자자 변경 시 해당 투자자의 증권계좌 목록을 동적으로 로드해야 함
  - 페이지네이션 상태는 필터 변경 시 리셋되어야 함

### 2.3 사용자 흐름 개선
**기존 흐름**: 
1. 투자자 ID 기억해서 입력 → 증권계좌 ID 기억해서 입력 → IPO ID 기억해서 입력 → 조회
2. 오류 시 전체 재입력

**개선된 흐름**:
1. 투자자 선택 드롭다운에서 선택 (이미 등록된 투자자 목록 표시)
2. 선택된 투자자의 증권계좌 목록이 자동으로 로드되어 드롭다운에 표시
3. 증권계좌 선택 드롭다운에서 선택
4. (선택 사항) 공모주 선택 드롭다운에서 필터링
5. 조회 버튼 클릭 또는 자동 조회
6. 결과 표시

## 3. 청약/상장/환불일 달력 뷰 설계안

### 3.1 데이터 소스 결정
- **주요 데이터 소스**: Epic 17에서 동기화한 전체 공모주 마스터 일정 (청약 시작일, 청약 마감일, 배정일, 상일일, 환불일)
- **보조 데이터 소스**: 사용자의 실제 청약 기록 (하이라이트 목적용)
- **중요 원칙**: 이 화면은 **로그인한 사용자의 청약 기록으로 필터링하는 화면이 아님**
  - 사용자 명시 결정: "이 달력은 사용자가 증권사 계좌를 몇 개 등록했는지와 무관하게 전체 공모주 일정을 다 띄워야 한다"
  - 따라서 계정 소유권 검증이 필요 없는 전역 데이터 뷰
  - 사용자 본인이 실제로 청약한 종목은 별도 강조 표시(하이라이트)로 고려 가능

### 3.2 캘린더 UI 구현 방식
- **현재 관례**: 이 프로젝트가 지금까지 외부 UI 라이브러리를 안 쓰고 직접 컴포넌트를 만들어온 관례 확인 필요
  - ui/package.json 조사 결과: `@tanstack/react-query`, `react-hook-form`, `react-router-dom` 등 상태 관리/폼 관련 라이브러리는 있으나 전용 캘린더 라이브러리 없음
  - 따라서 순수 CSS 그리드로 직접 구현하는 방향이 프로젝트 관례와 일치
- **구현 접근방법**:
  1. 월별 그리드 기반 캘린더 구성
  2. 각 날짜에 해당하는 IPO 이벤트 표시 (청약/상장/환불)
  3. 사용자의 청약 기록과 매치되는 IPO는 시각적 하이라이트 적용
  4. 이벤트 클릭 시 상세 정보 모달 또는 페이지 네비게이션

### 3.3 API 요구사항
- **필요한 신규 엔드포인트**: `GET /ipo-schedule` 또는 유사한 형태
  - 응답 형태: IPO별 날짜 이벤트 목록
    ```json
    [
      {
        "ipoId": 1,
        "ipoName": "예시공모주",
        "events": [
          {"type": "APPLICATION_START", "date": "2026-10-01"},
          {"type": "APPLICATION_END", "date": "2026-10-05"},
          {"type": "ALLOCATION_DATE", "date": "2026-10-10"},
          {"type": "LISTING_DATE", "date": "2026-10-15"},
          {"type": "REFUND_DATE", "date": "2026-10-12"}
        ]
      }
    ]
    ```
  - 계정 소유권 검증 불필요 (전역 데이터)
  - Epic 17에서 IPO 일정 자동 동기화 기능이 구현되면 이 엔드포인트 제공 가능

## 4. 월별 투자 손익 집계 페이지 설계안

### 4.1 기존 API 분석
- **현재 존재**: `GET /dashboard/by-period?unit=month&year=2026` (backend/src/main/java/com/ipocalc/dashboard/controller/DashboardController.java lines 62-77)
- **응답 형태**: PeriodAggregationDto 리스트 (월별 총투자금, 총세전/세후수익, 수익률, 참여건수)
- **대시보드 월별 탭**: ui/src/features/dashboard/useDashboardByPeriod.ts에서 이미 이 API를 호출하여 사용

### 4.2 사용자 요구사항 vs 기존 기능 비교
| 사용자 요구사항 | 기존 대시보드 월별 탭 | 차이점 |
|----------------|----------------------|--------|
| "이번 달 수익금을 한눈에 확인" | 월별 투자 손익 집계 제공 | 동일 |
| 별도 페이지가 필요한가? | 대시보드 내 월별 탭으로 존재 | 별도 페이지 불필요 |
| 청약/상장/환불 일정과 손익을 한 화면에 보여주는 것 | 손익 정보만 표시 | 추가 기능 필요 시 확장 고려 |

### 4.3 결론
- 기존 대시보드 월별 탭이 사용자 요구사항을 완전히 충족함
- 별도 페이지 신설 대신 대시보드 월별 탭의 접근성 개선 및 UX 개선이 더 적절
- 개선 방향:
  1. 현재 선택된 월을 더 tydily 표시 (현재는 작은 텍스트만)
  2. 전월/다음월 이동 버튼 추가로 직관적인 네비게이션 제공
  3. 차트 대신 단순 숫자표시 유지 (프로젝트 관례 준수)

## 5. 등록 폼 개편 방향 (Epic 17/18/19 연계)

### 5.1 Epic 17 완료 시 변화 (공모주 일정 자동 동기화)
- **청약 등록 폼**: 
  - 변경 전: 공모주 선택 드롭다운 (수동 등록된 IPO 목록)
  - 변경 후: 공모주 선택 드롭다운 (자동 동기화된 최신 IPO 목록 + 수동 등록 옵션)
  - 추가 기능: 선택된 공모주의 청약 일정 정보 표시 (청약 시작/마감일, 배정일 등)

### 5.2 Epic 18 완료 시 변화 (증권사 조회 전용 연동)
- **증권계좌 등록 폼**:
  - 변경 전: 계좌번호 텍스트 입력 (수동 입력 + 자동 하이픈)
  - 변경 후: 증권사 선택 드롭다운 → 해당 증권사의 계좌번호 조회/자동 채움 옵션 제공
  - 추가 기능: 계좌주명, 계좌 유형 등 자동 채움 가능성 탐색

### 5.3 매도 등록 폼 개선 방향
- **변경 전**: 투자자 선택 → 청약 선택 → 배정 선택 → 매도수량 입력
- **변경 후 (Epic 17/18 완성 시)**:
  1. 투자자 선택 드롭다운
  2. 선택된 투자자의 증권계좌별 보유 주식 현황 요약 표시
  3. 보유 주식 선택 시 자동으로 해당 청약/배정 정보 채움
  4. 매도수량 입력 (잔여 수량 범위 내 제한)
  5. 매도 단가/금액 입력 시 자동으로 예상 세금/수수료 계산 표시

### 5.4 세 Epic 의존관계 요약
```
Epic 17 (IPO 일정 자동 동기화) 
    → 제공 데이터: 자동 동기화된 IPO 목록 및 일정
    
Epic 18 (증권사 조회 전용 연동) 
    → 제공 데이터: 증권사별 계좌 조회 및 정보 자동 채움
    
Epic 19 (사후 등록 UX/UI 전면 개선) 
    → Epic 17/18의 제공 데이터를 활용한 폼/화면 개선
    → 투자자 중심의 직관적인 워크플로우 설계
    → 접근성 준수를 위한 랜드마크 및 지역 구조 개선
    
의존성: Epic 19는 Epic 17과 18의 일부 기능을 활용할 수 있지만, 
       독립적으로도 기본적인 UX 개선(드롭다운 선택, 접근성 개선)은 가능
```

## 6. 접근성 위반 연계

### 6.1 landmark-one-main/region 위반 분석
- docs/qa/accessibility-report.md의 landmark-one-main/region 위반 28건 조사 결과:
  - 거의 모든 페이지에서 `<html>` 또는 `<body>` 요소에 대한 위반으로 보고됨
  - 실제 원인: 페이지 루트에 적절한 `<main>` 랜드마크가 부재하고, 콘텐츠가	header, nav, main, footer 등의 시맨틱 요소로 적절히 구분되지 않음
  - 예시: 투자 내역 페이지의 경우 `<h1 class="_title_1n86u_10">투자 내역</h1>` 바로 뒤에 콘텐츠가 시작되지만 감싸는 시맨틱 요소가 없음

### 6.2 개편 시 동시 해결 방안
- **공통 레이아웃 컴포넌트 도입**: 
  - 모든 페이지에 적용 가능한 기본 레이아웃 컴포넌트 생성
  - 구조: `<header>` (네비게이션) + `<main>` (메인 콘텐츠) + `<footer>` (선택 사항)
  - 이렇게 하면 모든 자식 페이지가 자동으로 landmark-one-main/region 규칙을 준수
- **구체적 적용**:
  1. `src/components/layout/MainLayout.tsx` 생성
  2. App.tsx에서 모든 라우트를 이 레이아웃으로 감싸기
  3. 개별 페이지에서는 메인 콘텐츠만 담당하고 레이아웃은 처리하지 않음
- **투자 내역 페이지 적용 예시**:
  ```tsx
  // MainLayout.tsx
  <header>...</header>
  <main>
    {/* 여기서는 InvestmentHistoryPage의 기존 내용 */}
    <h1>투자 내역</h1>
    <AccountSelector />
    <FilterBar />
    <ResultsArea />
  </main>
  <footer>...</footer>
  ```

## 7. 검증 사항 및 결론

### 7.1 백엔드 API sufficiency 검증
- **투자 내역 조회**: 기존 API로 충분히 가능 (프론트엔드만 드롭다운 선택 방식으로 변경)
- **월별 손익 집계**: 기존 `/dashboard/by-period?unit=month` API로 충분히 가능
- **캘린더 뷰**: 신규 API 필요 (`GET /ipo-schedule`), 이는 Epic 17의 산출물
- **등록 폼 개선**: 대부분 현재 구조로 개선 가능, Epic 18로 더 향상 가능

### 7.2 Epic 17/18/19 의존관계 요약
- **선행 관계**: Epic 17과 18은 Epic 19의 향상을 위한 데이터 소스를 제공하지만 필수 전제조건은 아님
- **독립적 실행 가능성**: Epic 19는 다음을 독립적으로 달성 가능:
  1. 투자 내역 조회 화면을 드롭다운 선택 방식으로 개선 (접근성 향상 포함)
  2. 등록 폼들의 접근성 향상 및 사용성 개선 (기본 드롭다운 개선, 레이아웃 수정)
  3. 공통 레이아웃 컴포넌트를 도입하여 접근성 위반 28건 중 대다수 해결
- **Epic 17/18 의존 기능** (이미 구현되면 더 나은 UX 제공):
  1. 자동 완성/추천 기능이 있는 드롭다운
  2. 선택 시 관련 정보 자동 표시 및 폼 필드 자동 채움
  3. 외부 API 연동을 통한 실시간 데이터 제공

### 7.3 문서에서 다루지 못한/추가 조사가 필요한 부분
1. **캘린더 세부 디자인**: 구체적인 날짜 셀 크기, 이벤트 표시 방식, 하이라이트 색상 등에 대한 사용자 선호도 조사 필요
2. **증권사 조회 연동 범위**: Epic 18의 파일럿 대상 증권사(한국투자증권) 외 다른 증권사로의 확장 시 고려사항
3. **성능 영향**: 투자자별 많은 증권계좌를 보유한 사용자에 대한 계좌 목록 로딩 성능 영향 분석 필요
4. **반응형 디자인**: 모바일 환경에서의 캘린더 뷰 및 드롭다운 사용성 최적화 방안
5. **국제화 지원**: 향후 다국어 지원을 고려한 레이아웃 및 컴포넌트 설계