import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-a848c43-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-a848c43-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-a848c43-notice {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background: var(--info-bg);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--ink);
  }

  .s-a848c43-form {
    width: 100%;
    max-width: 520px;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-a848c43-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-a848c43-error {
    color: var(--danger);
    font-size: 14px;
  }

  .s-a848c43-warning {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background: var(--warning-bg);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--warning-ink);
  }

  .s-a848c43-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }
`

export default styles
