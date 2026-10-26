import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-17e93db-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-17e93db-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-17e93db-card {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow-card);
  }

  .s-17e93db-heading {
    margin: 0;
    font-size: 18px;
  }

  .s-17e93db-hint,
  .s-17e93db-help {
    margin: 0;
    font-size: 14px;
    color: var(--ink-muted);
  }

  .s-17e93db-notice,
  .s-17e93db-warning {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--ink);
    background: var(--info-bg);
    border: 1px solid var(--border);
  }

  .s-17e93db-warning {
    background: var(--warning-bg);
    border-color: var(--warning-border);
    color: var(--warning-ink);
  }

  .s-17e93db-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .s-17e93db-kinds {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .s-17e93db-kind {
    display: flex;
    align-items: flex-start;
    gap: var(--space-3);
    cursor: pointer;
  }

  .s-17e93db-kind input {
    width: 20px;
    height: 20px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .s-17e93db-kind span {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .s-17e93db-error {
    margin: 0;
    color: var(--danger);
    font-size: 14px;
  }
`

export default styles
