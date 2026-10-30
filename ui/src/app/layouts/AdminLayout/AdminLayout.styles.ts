import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-adl-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-adl-layout {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }
  .s-adl-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-6);
    height: var(--header-h);
    padding: 0 var(--gutter);
    background: rgba(255, 255, 255, 0.92);
    border-bottom: 1px solid var(--border);
    position: sticky;
    top: 0;
    z-index: 20;
  }
  .s-adl-brand {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    flex-shrink: 0;
  }
  .s-adl-siteName {
    font-size: 17px;
    font-weight: 800;
    color: var(--ink);
  }
  .s-adl-nav {
    display: flex;
    align-items: center;
    gap: var(--space-1);
  }
  .s-adl-link {
    display: inline-flex;
    align-items: center;
    height: 100%;
    padding: 0 14px;
    color: var(--ink-muted);
    font-size: 15px;
    font-weight: 600;
    text-decoration: none;
    border: 0;
    background: none;
    font-family: inherit;
    cursor: pointer;
  }
  .s-adl-link:hover {
    color: var(--ink);
  }
  .s-adl-linkActive {
    color: var(--primary);
    font-weight: 700;
    position: relative;
  }
  .s-adl-linkActive::after {
    content: '';
    position: absolute;
    left: 14px;
    right: 14px;
    bottom: -1px;
    height: 2px;
    background: var(--primary);
  }
  .s-adl-actions {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    flex-shrink: 0;
  }
  .s-adl-logoutButton {
    min-height: 44px;
    padding: 0 var(--space-5);
    background: var(--surface);
    color: var(--ink);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
  }
  .s-adl-logoutButton:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  .s-adl-main {
    flex: 1;
    padding: var(--space-6) var(--gutter) var(--space-8);
    background: var(--page-bg);
  }
`

export default styles
