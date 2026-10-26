import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-fe237c2-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-fe237c2-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    padding: var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-fe237c2-fieldGroup {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    flex: 1;
    min-width: 0;
  }

  .s-fe237c2-label {
    font-weight: 500;
    color: var(--ink);
    font-size: 14px;
  }

  .s-fe237c2-input::placeholder {
    color: var(--ink-muted);
  }

  .s-fe237c2-hint {
    font-size: 12px;
    color: var(--ink-muted);
  }

  .s-fe237c2-resetButton {
    align-self: flex-start;
    min-height: 44px;
    padding: var(--space-2) var(--space-4);
    background-color: var(--surface);
    color: var(--link);
    border: 1px solid var(--link);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition:
      background-color 0.2s,
      color 0.2s;
  }

  .s-fe237c2-resetButton:hover {
    background-color: var(--primary-hover);
    color: var(--on-primary);
  }

  .s-fe237c2-resetButton:focus {
    outline: none;
  }

  /* 반응형: 모바일(~640px) 세로 스택 */
  @media (max-width: 640px) {
    .s-fe237c2-container {
      padding: var(--space-3);
      gap: var(--space-3);
    }

    .s-fe237c2-resetButton {
      width: 100%;
      padding: var(--space-3);
    }
  }

  /* 태블릿(641px~1023px) */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-fe237c2-container {
      flex-direction: row;
      flex-wrap: wrap;
      align-items: flex-start;
    }

    .s-fe237c2-fieldGroup {
      flex: 1;
      min-width: 200px;
    }

    .s-fe237c2-resetButton {
      margin-top: 31px;
    }
  }

  /* 데스크톱(1024px~) 가로 배치 */
  @media (min-width: 1024px) {
    .s-fe237c2-container {
      flex-direction: row;
      flex-wrap: nowrap;
      align-items: flex-start;
      gap: var(--space-5);
    }

    .s-fe237c2-fieldGroup {
      flex: 1;
      min-width: 0;
    }

    .s-fe237c2-resetButton {
      margin-top: 31px;
      white-space: nowrap;
    }
  }
`

export default styles
