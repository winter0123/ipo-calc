import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ed0b413-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ed0b413-firms,
  .s-ed0b413-accounts {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-ed0b413-firm {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-ed0b413-firmHead,
  .s-ed0b413-accountMain,
  .s-ed0b413-accountActions,
  .s-ed0b413-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
  }

  .s-ed0b413-firmName {
    font-size: 17px;
    margin: 0;
    margin-right: auto;
  }

  .s-ed0b413-firmLogo {
    flex-shrink: 0;
    margin-right: var(--space-2);
  }

  .s-ed0b413-account {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    padding: var(--space-3);
    border-radius: var(--radius-sm);
    background: var(--surface-soft);
  }

  .s-ed0b413-number {
    font-family: var(--mono);
    font-weight: 650;
    color: var(--ink);
    overflow-wrap: anywhere;
  }

  .s-ed0b413-alias {
    color: var(--ink-muted);
  }

  .s-ed0b413-badge,
  .s-ed0b413-badgeLink {
    font-size: 12px;
    font-weight: 650;
    padding: 2px var(--space-2);
    border-radius: 999px;
    background: var(--success-bg);
    color: var(--success-ink);
    border: 1px solid var(--success-border);
  }

  .s-ed0b413-badgeLink {
    background: var(--surface-sea);
    color: var(--link);
    border-color: var(--border);
    text-decoration: none;
  }

  .s-ed0b413-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    max-width: 420px;
  }

  .s-ed0b413-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-ed0b413-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-ed0b413-check {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 14px;
    min-height: 44px;
  }

  .s-ed0b413-fieldError {
    color: var(--danger);
    font-size: 13px;
  }

  .s-ed0b413-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    margin-bottom: var(--space-3);
  }

  .s-ed0b413-sectionTitle {
    font-size: 15px;
    color: var(--ink-muted);
    margin: var(--space-4) 0 var(--space-2);
  }

  /* 등록 안 한 증권사는 촘촘한 격자로(휴대폰은 1열) */
  .s-ed0b413-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: var(--space-2);
  }

  .s-ed0b413-grid > .s-ed0b413-firm {
    padding: var(--space-2) var(--space-3);
  }

  .s-ed0b413-grid .s-ed0b413-firmName {
    font-size: 15px;
  }

  /* 등록 폼이 열린 칸은 한 줄 전체를 쓴다 */
  .s-ed0b413-wide {
    grid-column: 1 / -1;
  }
`

export default styles
