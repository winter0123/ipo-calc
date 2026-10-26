import { createGlobalStyle } from 'styled-components'

const GlobalStyles = createGlobalStyle`
/*
 * 디자인 토큰: 색은 primary 하나 + 회색 계열 + 의미 색(성공·위험·경고)만 쓴다. 장식용 그라데이션·색 면은 쓰지 않는다.
 * 대비는 WCAG 계산값(값 옆 주석).
 */
:root {
  --white: #ffffff;

  /* 글자·링크 (파생색) */
  --ink: #17263d; /* 흰 배경 16:1 */
  --ink-muted: #52647a; /* 흰 배경 7.5:1 */
  --link: #1b63bd; /* 흰 배경 5.4:1 */
  --link-hover: #174e96; /* 흰 배경 6.9:1 */

  /* 면 */
  --surface: #ffffff;
  --surface-soft: #f4f7fb;
  --surface-sea: #edf5ff; /* primary 옅은 틴트: 선택·호버 */

  /* 선 */
  --border: #dce5ef;
  --border-strong: #8293a8; /* 입력창 등 UI 경계, 흰 배경 3.6:1 */

  /* 기본 동작(버튼 등): 브랜드 파랑을 한 단계 진하게 한 "깊은 바다". 흰 글자 5.0:1 */
  --primary: #1e6fcb;
  --primary-hover: #195caa; /* 흰 글자 6.6:1 */
  --primary-active: #154e92;
  --on-primary: #ffffff;
  --primary-deep: #174e96;
  --info-border: #c7ddf6;

  /* 바탕: 옅은 회색 위에 1px 선의 흰 카드 */
  --page-bg: #f2f6fb;

  /* 상태 */
  --success-bg: #e9f8d4;
  --success-border: #b7d99a;
  --success-ink: #2e5a0b;
  /* 손익: 국내 관례(이익 빨강·손실 파랑). 흰 배경 대비 5.1:1 / 5.5:1. 오류는 --danger */
  --gain: #cf2e2e;
  --loss: #1f63d6;
  --danger: #b3261e;
  --danger-bg: #fdecea;
  --danger-border: #f2b8b5;
  --warning-bg: #fff3d1;
  --warning-border: #f0c24b;
  --warning-ink: #6b4300;
  --info-bg: var(--surface-sea);

  /* 간격: 8pt 그리드(4pt 보조) — 화면별로 임의 값을 쓰지 않는다 */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;
  --space-7: 48px;
  --space-8: 64px;

  /* 모양: 카드는 1px 선으로 구분하고 그림자는 팝업에만 쓴다 */
  --radius-sm: 8px; /* 버튼·입력창 */
  --radius: 16px; /* 카드 */
  --radius-lg: 22px;
  --shadow-card: 0 12px 34px rgba(25, 54, 91, 0.07);
  --shadow-pop: 0 8px 24px rgba(27, 36, 48, 0.12);
  --focus-ring: 0 0 0 3px rgba(67, 155, 245, 0.45);

  /* 레이아웃 */
  --content-max: 1280px;
  --gutter: 24px;
  --header-h: 64px;

  /* 글꼴 */
  --sans: 'Pretendard Variable', Pretendard, system-ui, 'Segoe UI', Roboto, sans-serif;
  --heading: var(--sans);
  --mono: ui-monospace, Consolas, monospace;

  /* 기존 이름 호환(일부 모듈이 아직 사용) */
  --text: var(--ink-muted);
  --text-h: var(--ink);
  --bg: var(--page-bg);
  --accent: var(--link);
  --code-bg: var(--surface-sea);

  font: 16px/1.6 var(--sans);
  letter-spacing: -0.01em;
  color-scheme: light;
  color: var(--ink);
  background: var(--page-bg);
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-height: 100svh;
  background: radial-gradient(ellipse at 2% 0%, rgba(67, 155, 245, 0.11), transparent 36%), var(--page-bg);
}

#root {
  width: 100%;
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}

/* 제목 스케일: 화면마다 따로 정하지 않는다 */
h1,
h2,
h3 {
  font-family: var(--heading);
  color: var(--ink);
  letter-spacing: -0.02em;
  margin: 0;
}

h1 {
  font-size: clamp(28px, 3vw, 36px);
  font-weight: 780;
  line-height: 1.3;
}

h2 {
  font-size: 20px;
  font-weight: 740;
  line-height: 1.4;
}

h3 {
  font-size: 16px;
  font-weight: 650;
  line-height: 1.4;
}

p {
  margin: 0;
}

a {
  color: var(--link);
  text-underline-offset: 3px;
}

a:hover {
  color: var(--link-hover);
}

:focus-visible {
  outline: 2px solid var(--link);
  outline-offset: 2px;
}

code {
  font-family: var(--mono);
  font-size: 0.9em;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--code-bg);
  color: var(--ink);
}

/* 폼 컨트롤 공통: 높이 44, 라벨은 위, 선 1px */
input:not([type='checkbox']):not([type='radio']),
select,
textarea {
  font: inherit;
  font-size: 15px;
  color: var(--ink);
  width: 100%;
  min-height: 44px;
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

textarea {
  min-height: 88px;
  resize: vertical;
}

input::placeholder,
textarea::placeholder {
  color: var(--ink-muted);
  opacity: 0.75;
}

input:not([type='checkbox']):not([type='radio']):hover:not(:disabled),
select:hover:not(:disabled),
textarea:hover:not(:disabled) {
  border-color: var(--link);
}

input:not([type='checkbox']):not([type='radio']):focus,
select:focus,
textarea:focus {
  outline: none;
  border-color: var(--link);
  box-shadow: var(--focus-ring);
}

input:disabled,
select:disabled,
textarea:disabled {
  background: var(--surface-sea);
  color: var(--ink-muted);
  cursor: not-allowed;
}

input[type='checkbox'],
input[type='radio'] {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: var(--link);
}

label {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

/* 표 공통: 행 48px, 얇은 가로선, 헤더는 작고 굵은 보조색 */
table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: var(--space-3) var(--space-4);
  text-align: left;
  vertical-align: middle;
  border-bottom: 1px solid var(--border);
}

th {
  background: var(--surface-sea);
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
}

tbody tr:hover td {
  background: var(--surface-sea);
}

@media (max-width: 1024px) {
  :root {
    --gutter: 16px;
  }

  h1 {
    font-size: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  * {
    transition: none !important;
    animation: none !important;
  }
}

html .profit-gain {
  color: var(--gain);
}

html .profit-loss {
  color: var(--loss);
}

`

export default GlobalStyles
