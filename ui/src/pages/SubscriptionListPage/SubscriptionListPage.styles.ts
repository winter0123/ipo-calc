import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-d6e906b-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-d6e906b-container {
    --page-max: 760px;
  }

  .s-d6e906b-status {
    padding: var(--space-4);
    text-align: center;
    color: var(--ink-muted);
  }

  .s-d6e906b-error {
    padding: var(--space-2) var(--space-3);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--danger);
  }

  .s-d6e906b-titleRow {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
  }

  .s-d6e906b-buttonGroup {
    display: flex;
    gap: var(--space-2);
  }

  .s-d6e906b-sellButton {
    padding: var(--space-2) var(--space-4);
    background-color: var(--surface);
    color: var(--ink);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-d6e906b-sellButton:hover {
    background-color: var(--surface-soft);
    border-color: var(--ink-muted);
  }

  .s-d6e906b-registerButton {
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-d6e906b-registerButton:hover {
    background-color: var(--primary-hover);
  }

  .s-d6e906b-dateRange {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
    margin-top: var(--space-4);
  }
  .s-d6e906b-dateRange label {
    display: grid;
    gap: var(--space-1);
    color: var(--ink-muted);
  }
  .s-d6e906b-dateRange input {
    min-height: 44px;
    padding: 0 var(--space-2);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    color: var(--ink);
  }
`

export default styles
