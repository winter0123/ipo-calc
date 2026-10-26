import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ac6580a-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ac6580a-container {
    --page-max: 1200px;
  }

  .s-ac6580a-querySection {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-ac6580a-inputGroup {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: var(--space-3);
  }

  .s-ac6580a-label {
    font-size: 14px;
    font-weight: 500;
    color: var(--ink);
    margin-bottom: var(--space-1);
    display: block;
  }

  .s-ac6580a-input,
  .s-ac6580a-select {
    flex: 1;
    min-width: 200px;
  }

  .s-ac6580a-input:disabled,
  .s-ac6580a-select:disabled {
    cursor: not-allowed;
  }

  .s-ac6580a-searchButton {
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
    transition: background-color 0.2s;
    white-space: nowrap;
  }

  .s-ac6580a-presetButton {
    padding: var(--space-2) var(--space-4);
    background-color: var(--surface);
    color: var(--link);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
    white-space: nowrap;
  }

  .s-ac6580a-searchButton:hover:not(:disabled) {
    background-color: var(--primary-hover);
  }

  .s-ac6580a-searchButton:disabled {
    background-color: var(--surface-sea);
    color: var(--ink-muted);
    cursor: not-allowed;
  }

  .s-ac6580a-error {
    padding: var(--space-3) var(--space-4);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-ac6580a-loading {
    padding: var(--space-4);
    text-align: center;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-ac6580a-empty {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
    font-size: 14px;
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  @media (min-width: 640px) {
    .s-ac6580a-inputGroup {
      flex-direction: row;
    }
  }

  /* 휴대폰: 날짜 입력은 나란히, 기간 프리셋 4개는 한 줄 묶음으로 */
  @media (max-width: 640px) {
    .s-ac6580a-inputGroup > div {
      flex: 1 1 calc(50% - var(--space-3));
      min-width: 0;
    }

    .s-ac6580a-input {
      min-width: 0;
      width: 100%;
    }

    .s-ac6580a-presetButton,
    .s-ac6580a-searchButton {
      flex: 1 1 0;
      min-width: 0;
      padding: var(--space-2);
    }
  }

  .s-ac6580a-hint {
    margin: 0;
    color: var(--ink-muted);
    font-size: 13px;
  }
`

export default styles
