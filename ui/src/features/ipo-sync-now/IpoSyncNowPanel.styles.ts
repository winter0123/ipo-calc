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
    gap: var(--space-2);
    width: 100%;
    max-width: 520px;
    padding: var(--space-3) var(--space-4);
    margin: var(--space-3) 0;
    background: var(--surface-sea);
    border: 1px solid var(--info-border);
    border-radius: var(--radius);
  }

  .s-7a498b2-panelHeader {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-1);
    padding-bottom: var(--space-1);
    border-bottom: 1px solid var(--info-border);
    width: 100%;
  }

  .s-7a498b2-panelLabel {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.02em;
    text-transform: uppercase;
    color: var(--on-primary);
    background: var(--primary);
    border-radius: 999px;
  }

  .s-7a498b2-panelTitle {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-7a498b2-range {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
    width: 100%;
  }

  .s-7a498b2-field {
    display: flex;
    flex-direction: column;
    gap: 3px;
    font-size: 14px;
    flex: 1;
    min-width: 130px;
  }

  .s-7a498b2-field label {
    font-size: 12px;
    font-weight: 600;
    color: var(--ink);
  }

  .s-7a498b2-field input {
    min-height: 38px;
    padding: 6px var(--space-3);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    font-size: 13px;
  }

  .s-7a498b2-field input:focus {
    outline: none;
    border-color: var(--link);
    box-shadow: var(--focus-ring);
  }

  .s-7a498b2-description {
    margin: 0;
    color: var(--ink-muted);
    font-size: 12px;
    line-height: 1.4;
  }

  .s-7a498b2-button {
    min-height: 40px;
    padding: 0 var(--space-4);
    background: var(--primary);
    color: var(--on-primary);
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 14px;
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
    font-size: 13px;
    color: var(--ink);
  }

  .s-7a498b2-status:empty {
    display: none;
  }

  .s-7a498b2-error {
    padding: var(--space-2) var(--space-3);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 13px;
  }
`

export default styles