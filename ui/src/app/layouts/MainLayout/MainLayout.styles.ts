import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-91ad696-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-91ad696-layout {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }

  .s-91ad696-skipLink {
    position: absolute;
    top: -100%;
    left: 50%;
    transform: translateX(-50%);
    padding: var(--space-3) var(--space-5);
    background: var(--surface);
    color: var(--ink);
    border: 2px solid var(--ink);
    text-decoration: none;
    font-weight: 600;
    border-radius: var(--radius-sm);
    z-index: 100;
    transition: top 0.2s;
  }

  .s-91ad696-skipLink:focus {
    top: var(--space-4);
    outline: 3px solid var(--link);
    outline-offset: 2px;
  }

  /* 상단 바: 흰 배경 + 아래 1px 구분선 */
  .s-91ad696-header {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: var(--space-6);
    height: var(--header-h);
    padding: 0 var(--gutter);
    background: rgba(255, 255, 255, 0.92);
    border-bottom: 1px solid rgba(220, 229, 239, 0.85);
    box-shadow: 0 4px 20px rgba(25, 54, 91, 0.035);
    backdrop-filter: blur(16px);
  }

  .s-91ad696-brand {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    flex-shrink: 0;
  }

  .s-91ad696-logo {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    color: var(--primary);
  }

  .s-91ad696-guestLogo {
    text-decoration: none;
  }

  .s-91ad696-siteName {
    font-size: 17px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--ink);
  }

  .s-91ad696-nav {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    height: 100%;
    margin-left: auto;
  }

  .s-91ad696-link,
  .s-91ad696-logout {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    height: 100%;
    padding: 0 14px;
    color: var(--ink-muted);
    font-size: 15px;
    font-weight: 600;
    text-decoration: none;
    white-space: nowrap;
    cursor: pointer;
    list-style: none;
    border: 0;
    background: none;
    font-family: inherit;
  }

  .s-91ad696-link:hover,
  .s-91ad696-logout:hover {
    color: var(--ink);
  }

  .s-91ad696-link:focus-visible,
  .s-91ad696-logout:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: -4px;
    border-radius: var(--radius-sm);
  }

  .s-91ad696-logout:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .s-91ad696-active,
  .s-91ad696-active:hover {
    color: var(--primary);
    font-weight: 700;
  }

  .s-91ad696-active::after {
    content: '';
    position: absolute;
    left: 14px;
    right: 14px;
    bottom: -1px;
    height: 2px;
    background: var(--primary);
  }

  .s-91ad696-group {
    position: relative;
    display: flex;
  }

  .s-91ad696-groupTitle {
    display: none;
  }

  .s-91ad696-morePanel {
    position: absolute;
    top: calc(100% + var(--space-1));
    right: 0;
    z-index: 30;
    display: flex;
    flex-direction: column;
    min-width: 140px;
    padding: var(--space-2);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow-pop);
  }

  /* display: flex가 hidden 속성을 덮어쓰지 않게 한다(모바일은 아래 미디어 쿼리에서 다시 펼친다) */
  .s-91ad696-morePanel[hidden] {
    display: none;
  }

  .s-91ad696-moreLink {
    display: block;
    width: 100%;
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-sm);
    border: 0;
    background: transparent;
    color: var(--ink);
    font-size: 15px;
    font-family: inherit;
    text-align: left;
    text-decoration: none;
    white-space: nowrap;
    cursor: pointer;
  }

  .s-91ad696-moreLinkActive {
    font-weight: 700;
    background: var(--surface-sea);
  }

  .s-91ad696-moreLink:hover {
    background: var(--surface-sea);
  }

  /* 모바일에서만 보임. 좌측 물결 자리에 두고 햄버거 아이콘 대신 Waves를 쓴다 */
  .s-91ad696-menuButton {
    display: none;
    align-items: center;
    justify-content: center;
    gap: var(--space-2);
    width: 44px;
    height: 44px;
    padding: 0;
    background: transparent;
    border: 0;
    border-radius: var(--radius-sm);
    color: var(--primary);
    font: inherit;
    cursor: pointer;
  }

  .s-91ad696-menuButton:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
  }

  .s-91ad696-menuLabel {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  .s-91ad696-main {
    flex: 1;
    width: 100%;
    max-width: calc(var(--content-max) + 2 * var(--gutter));
    margin: 0 auto;
    padding: clamp(24px, 4vw, 48px) var(--gutter) clamp(40px, 6vw, 72px);
  }

  /* 모든 화면 루트: 같은 폭 규칙의 흰 카드(가운데 정렬), 제목과 본문 사이 같은 간격 */
  .s-91ad696-main > * {
    display: flex;
    flex-direction: column;
    gap: clamp(24px, 3.5vw, 40px);
    width: 100%;
    max-width: var(--page-max, var(--content-max));
    margin-inline: auto;
    padding: clamp(22px, 4vw, 44px);
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid rgba(220, 229, 239, 0.9);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-card);
  }

  @media (max-width: 900px) {
    .s-91ad696-header {
      gap: var(--space-3);
    }

    .s-91ad696-logo {
      display: none;
    }

    .s-91ad696-guestLogo {
      display: inline-flex;
    }

    .s-91ad696-menuButton {
      display: inline-flex;
    }

    .s-91ad696-nav {
      display: none;
      position: absolute;
      top: var(--header-h);
      left: 0;
      right: 0;
      flex-direction: column;
      align-items: stretch;
      gap: 0;
      height: auto;
      margin: 0;
      padding: var(--space-2) var(--gutter) var(--space-4);
      background: var(--surface);
      border-bottom: 1px solid var(--border);
    }

    /* 모바일에서 내비는 absolute라 투자자/인증 버튼을 우측으로 민다 */
    .s-91ad696-investorChip,
    .s-91ad696-authActions {
      margin-left: auto;
    }

    .s-91ad696-navOpen {
      display: flex;
    }

    .s-91ad696-link,
    .s-91ad696-logout {
      height: 48px;
      padding: 0 var(--space-3);
      border-radius: var(--radius-sm);
    }

    .s-91ad696-link:hover,
    .s-91ad696-logout:hover {
      background: var(--surface-soft);
    }

    .s-91ad696-active,
    .s-91ad696-active:hover {
      background: var(--surface-sea);
      color: var(--primary);
    }

    .s-91ad696-active::after {
      display: none;
    }

    /* 모바일: 그룹 버튼 대신 소제목, 목록은 항상 펼침 */
    .s-91ad696-group {
      flex-direction: column;
    }

    .s-91ad696-group > button {
      display: none;
    }

    .s-91ad696-groupTitle {
      display: flex;
      align-items: center;
      min-height: 48px;
      margin-top: var(--space-2);
      padding: 0 var(--space-3);
      color: var(--ink);
      font-size: 14px;
      font-weight: 800;
      background: var(--surface-soft);
      border-radius: var(--radius-sm);
    }

    .s-91ad696-nav > .s-91ad696-link {
      color: var(--ink);
      font-weight: 800;
      background: var(--surface-soft);
    }

    .s-91ad696-morePanel,
    .s-91ad696-morePanel[hidden] {
      display: flex;
      position: static;
      min-width: 0;
      margin: 2px 0 var(--space-2) var(--space-3);
      padding: 0 0 0 var(--space-2);
      border: 0;
      border-left: 2px solid var(--border);
      box-shadow: none;
    }

    .s-91ad696-moreLink {
      display: flex;
      align-items: center;
      min-height: 44px;
      padding-left: var(--space-4);
      color: var(--ink-muted);
    }

    .s-91ad696-main {
      padding: var(--space-4) var(--gutter) var(--space-7);
    }

    .s-91ad696-main > * {
      padding: var(--space-5) var(--space-4);
      gap: var(--space-5);
      border-radius: var(--radius);
    }
  }

  /* 현재 투자자: 이니셜 아바타 + 이름이 한 덩어리인 알약 */
  .s-91ad696-investorChip {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    flex-shrink: 0;
    height: 40px;
    padding: 0 var(--space-3) 0 var(--space-1);
    background: var(--white);
    border: 1px solid var(--border-strong);
    border-radius: 999px;
    color: var(--ink);
    font-size: 14px;
    font-weight: 650;
    white-space: nowrap;
  }

  .s-91ad696-investorChipButton {
    padding-right: var(--space-4);
    cursor: pointer;
  }

  .s-91ad696-investorChipButton:hover {
    border-color: var(--primary);
  }

  .s-91ad696-investorChipButton:focus-within {
    outline: 3px solid var(--focus-ring);
    outline-offset: 2px;
  }

  .s-91ad696-avatar {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: var(--primary);
    color: var(--on-primary);
    font-size: 14px;
    font-weight: 700;
  }

  .s-91ad696-chipName {
    max-width: 140px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* select는 접근성을 위해 실제 요소로 두되 모양은 벗겨 낸다 */
  .s-91ad696-chipSelect {
    appearance: none;
    min-height: 0;
    max-width: 140px;
    padding: 0 var(--space-3) 0 0;
    margin: 0;
    background: transparent;
    border: none;
    box-shadow: none;
    color: inherit;
    font: inherit;
    cursor: pointer;
    text-overflow: ellipsis;
  }

  .s-91ad696-chipSelect:focus,
  .s-91ad696-chipSelect:focus-visible {
    outline: none;
    box-shadow: none;
  }

  .s-91ad696-chevron {
    position: absolute;
    right: var(--space-3);
    pointer-events: none;
    color: var(--ink-muted);
  }

  .s-91ad696-srOnly {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  .s-91ad696-gate {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    max-width: 520px;
    margin: var(--space-7) auto;
    padding: var(--space-6);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    color: var(--ink);
  }

  .s-91ad696-gateButtons {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
  }

  .s-91ad696-gateButtons button {
    min-height: 44px;
    padding: 0 var(--space-5);
    background: var(--primary);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-weight: 650;
    cursor: pointer;
  }

  .s-91ad696-gateLink {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    padding: 0 var(--space-5);
    background: var(--primary);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
    font-weight: 650;
    text-decoration: none;
  }

  .s-91ad696-gateLink:hover {
    background: var(--primary-hover);
  }

  .s-91ad696-gateButtons button:hover {
    background: var(--primary-hover);
  }

  @media (max-width: 900px) {
    .s-91ad696-siteName {
      display: none;
    }

    .s-91ad696-chipSelect,
    .s-91ad696-chipName {
      max-width: 96px;
    }
  }

  /* 전환기가 들어간 헤더가 1400px 이하에서 넘치지 않도록 사이트 이름 글자는 줄인다(로고는 유지) */
  @media (max-width: 1400px) {
    .s-91ad696-siteName {
      display: none;
    }
  }

  /* 비로그인: 로그인·가입하기는 메뉴 줄이 아니라 헤더 우측 끝 버튼 */
  .s-91ad696-authActions {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    flex-shrink: 0;
  }

  .s-91ad696-calendarButton,
  .s-91ad696-loginButton,
  .s-91ad696-signupButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-4);
    border-radius: 999px;
    font-size: 15px;
    font-weight: 700;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-91ad696-calendarButton,
  .s-91ad696-loginButton {
    color: var(--primary);
    border: 1px solid var(--primary);
    background: transparent;
  }

  .s-91ad696-calendarButton:hover,
  .s-91ad696-loginButton:hover {
    background: var(--surface-sea);
  }

  .s-91ad696-signupButton {
    color: var(--on-primary);
    background: var(--primary);
    border: 1px solid var(--primary);
  }

  .s-91ad696-signupButton:hover {
    background: var(--primary-hover);
  }

  .s-91ad696-calendarButton:focus-visible,
  .s-91ad696-loginButton:focus-visible,
  .s-91ad696-signupButton:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
  }

  @media (max-width: 900px) {
    .s-91ad696-calendarButton,
    .s-91ad696-loginButton,
    .s-91ad696-signupButton {
      padding: 0 var(--space-3);
      font-size: 14px;
    }
  }

  /* 소개 페이지: 앱 화면용 흰 카드 없이 풀폭 섹션으로 그린다 */
  .s-91ad696-mainWide,
  .s-91ad696-mainWide > * {
    max-width: none;
    padding: 0;
    gap: 0;
    background: transparent;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }
`

export default styles
