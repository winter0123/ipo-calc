# 접근성 검사 보고서 (WCAG 2.1 AA / axe-core)

검사 일시: 2026. 9. 29. 오전 10:18:57
검사 도구: axe-core @axe-core/playwright
대상 URL: http://localhost:5173

## 요약

- 검사한 화면: 12개
- 건너뛴 화면: 0개
- 오류 발생 화면: 0개
- 총 위반 건수: 32건 (critical: 1, serious: 9, moderate: 28, minor: 0)

## 심각도 critical/serious 위반이 있는 화면 (상위)

- **investments** (/investments): 3건
- **login** (/login): 1건
- **signup** (/signup): 1건
- **investor-register** (/investors/register): 1건
- **securities-account-register** (/securities-accounts/register/1): 1건
- **subscription-list** (/subscriptions): 1건
- **allocation-register** (/allocations/register): 1건
- **dashboard** (/dashboard): 1건

## 화면별 상세 결과

### login (/login)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="submit" class="_submitButton_137zc_42">로그인</button>` (selector: button)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1>로그인</h1>` (selector: h1)

### signup (/signup)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="submit" class="_submitButton_137zc_42">가입하기</button>` (selector: button)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1>회원가입</h1>` (selector: h1)

### investor-register (/investors/register)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="submit" class="_submitButton_1uigw_65">등록</button>` (selector: button)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_1mbj9_8">투자자 등록</h1>` (selector: h1)

### securities-account-register (/securities-accounts/register/1)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="submit" class="_submitButton_1c7a7_87">등록</button>` (selector: button)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_1mbj9_8">증권계좌 등록</h1>` (selector: h1)

### subscription-register (/subscriptions/register)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<div id="root"><div class="_container_1mbj9_1"><h1 class="_title_1mbj9_8">청약 등록</h1><div class="_loading_19wev_97">목록을 불러오는 중...</div></div></div>` (selector: #root)

### subscription-list (/subscriptions)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="button" class="_button_ebudz_7" aria-pressed="true">전체</button>` (selector: button:nth-child(1))

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<div id="root">` (selector: #root)

### allocation-register (/allocations/register)

#### SERIOUS (1개 규칙, 1개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (1개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<button type="button" class="_button_ebudz_7" aria-pressed="true">전체</button>` (selector: button:nth-child(1))

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<div id="root">` (selector: #root)

### sale-register (/sales/register)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_x0b7n_8">매도 등록</h1>` (selector: h1)

### cost-register (/costs/register)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_1sm6f_7">비용 등록</h1>` (selector: h1)

### profit-view (/profits/view)

#### MODERATE (2개 규칙, 5개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (4개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_pt7j6_8">수익 확인</h1>` (selector: h1)
    - `<div class="_toggle_1vam5_1" role="radiogroup" aria-label="수익 조회 모드">` (selector: ._toggle_1vam5_1)
    - `<label for="saleId" class="_label_pt7j6_27">매도건 ID</label>` (selector: ._label_pt7j6_27)
    - ... 외 1개 더

### investments (/investments)

#### SERIOUS (1개 규칙, 3개 요소)

- **color-contrast**: Elements must meet minimum color contrast ratio thresholds (3개 요소)
  - 설명: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/color-contrast?application=playwright
  - 영향: serious
  - 예시 요소:
    - `<span id="investorId-hint" class="_hint_crqu3_45">비워두면 전체 조회</span>` (selector: #investorId-hint)
    - `<span id="securitiesAccountId-hint" class="_hint_crqu3_45">비워두면 전체 조회</span>` (selector: #securitiesAccountId-hint)
    - `<span id="ipoId-hint" class="_hint_crqu3_45">비워두면 전체 조회</span>` (selector: #ipoId-hint)

#### MODERATE (2개 규칙, 2개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (1개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_1n86u_10">투자 내역</h1>` (selector: h1)

### dashboard (/dashboard)

#### CRITICAL (1개 규칙, 1개 요소)

- **aria-valid-attr-value**: ARIA attributes must conform to valid values (1개 요소)
  - 설명: Ensure all ARIA attributes have valid values
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/aria-valid-attr-value?application=playwright
  - 영향: critical
  - 예시 요소:
    - `<button type="button" role="tab" aria-selected="true" aria-controls="tabpanel-all" id="tab-all" tabindex="0" class="_tab_1hovg_1 _activeTab_1hovg_32">전체</button>` (selector: #tab-all)

#### MODERATE (2개 규칙, 3개 요소)

- **landmark-one-main**: Document should have one main landmark (1개 요소)
  - 설명: Ensure the document has a main landmark
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/landmark-one-main?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<html lang="en">` (selector: html)

- **region**: All page content should be contained by landmarks (2개 요소)
  - 설명: Ensure all page content is contained by landmarks
  - 도움말: https://dequeuniversity.com/rules/axe/4.13/region?application=playwright
  - 영향: moderate
  - 예시 요소:
    - `<h1 class="_title_nqj8g_10">대시보드</h1>` (selector: h1)
    - `<div class="_tabList_1hovg_1" role="tablist" aria-label="대시보드 탭">` (selector: ._tabList_1hovg_1)

---

*이 보고서는 axe-core 자동 검사 결과에 기반합니다. 실제 사용자 테스트와 전문가 검토를 병행하시기 바랍니다.*
