import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-7a498b2-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-7a498b2-panel {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
    width: 100%;
    max-width: 520px;
  }

  .s-7a498b2-range {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
  }

  .s-7a498b2-field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 14px;
  }

  .s-7a498b2-description {
    margin: 0;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-7a498b2-button {
    min-height: 44px;
    padding: 0 var(--space-5);
    background: var(--primary);
    color: var(--on-primary);
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 15px;
    font-weight: 650;
    cursor: pointer;
  }

  .s-7a498b2-button:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-7a498b2-button:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: progress;
  }

  .s-7a498b2-status {
    font-size: 14px;
    color: var(--ink);
  }

  .s-7a498b2-status:empty {
    display: none;
  }

  .s-7a498b2-error {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }
`

export default styles
