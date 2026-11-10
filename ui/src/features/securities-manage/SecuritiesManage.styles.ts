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
    border-radius: 10px;
    padding: 8px 12px;
    display: grid;
    grid-template-columns: minmax(220px, 0.8fr) minmax(0, 1.2fr);
    align-items: center;
    gap: 12px;
  }

  .s-ed0b413-firmHead,
  .s-ed0b413-accountMain,
  .s-ed0b413-accountActions,
  .s-ed0b413-actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .s-ed0b413-firmHead {
    flex-wrap: nowrap;
    min-width: 0;
  }

  .s-ed0b413-firmName {
    font-size: 15px;
    margin: 0;
    margin-right: auto;
    white-space: nowrap;
  }

  .s-ed0b413-firmLogo {
    flex-shrink: 0;
    margin-right: 2px;
  }

  .s-ed0b413-firmCheckbox {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
    min-height: 40px;
    min-width: 40px;
  }

  .s-ed0b413-firmCheckbox input {
    min-width: 20px;
    min-height: 20px;
    cursor: pointer;
  }

  .s-ed0b413-firmCheckbox input:disabled {
    cursor: not-allowed;
  }

  .s-ed0b413-account {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    padding: 2px 0;
    border-radius: var(--radius-sm);
    background: transparent;
  }

  .s-ed0b413-number {
    font-family: var(--mono);
    font-size: 13px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
  }

  .s-ed0b413-numberNone {
    font-size: 13px;
    color: var(--ink-muted);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 1px var(--space-2);
  }

  .s-ed0b413-alias {
    color: var(--ink-muted);
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

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

  .s-ed0b413-fieldHint {
    color: var(--ink-muted);
    font-size: 13px;
  }

  .s-ed0b413-fieldHintBlock {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-2);
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
    grid-template-columns: minmax(0, 1fr);
  }

  .s-ed0b413-grid .s-ed0b413-firmName {
    font-size: 15px;
  }

  /* 등록 폼이 열린 칸은 한 줄 전체를 쓴다 */
  .s-ed0b413-wide {
    grid-column: 1 / -1;
  }

  .s-ed0b413-accounts {
    min-width: 0;
    gap: 2px;
  }

  .s-ed0b413-accountMain,
  .s-ed0b413-accountActions {
    flex-wrap: nowrap;
    gap: 6px;
    min-width: 0;
  }

  .s-ed0b413-accountActions > button,
  .s-ed0b413-firmHead > button {
    min-height: 40px;
    padding: 0 10px;
    border-radius: 8px;
    font-size: 12px;
    white-space: nowrap;
  }

  .s-ed0b413-form,
  .s-ed0b413-wide > .s-ed0b413-form,
  .s-ed0b413-wide > .s-ed0b413-formError {
    grid-column: 1 / -1;
  }

  .s-ed0b413-firm > .s-ed0b413-formError {
    grid-column: 1 / -1;
  }

  @media (max-width: 760px) {
    .s-ed0b413-firms:not(.s-ed0b413-grid) > .s-ed0b413-firm {
      grid-template-columns: minmax(0, 1fr);
      gap: 4px;
    }

    .s-ed0b413-account {
      flex-wrap: wrap;
    }

    .s-ed0b413-accountActions {
      margin-left: auto;
    }
  }
`

export default styles
