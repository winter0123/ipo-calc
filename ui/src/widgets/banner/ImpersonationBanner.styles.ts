import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-impbn-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-impbn-banner {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
    background: var(--warning-bg);
    color: var(--warning-ink);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius);
    font-size: 14px;
    font-weight: 600;
  }
  .s-impbn-label {
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    opacity: 0.8;
  }
  .s-impbn-readonly {
    border: 1px solid var(--warning-border);
    color: var(--warning-ink);
    border-radius: var(--radius-sm);
    padding: 0 var(--space-2);
    font-size: 12px;
  }
  .s-impbn-endButton {
    min-height: 44px;
    padding: 0 var(--space-4);
    background: var(--danger);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-weight: 700;
    cursor: pointer;
  }
  .s-impbn-endButton:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  .s-impbn-error {
    margin-left: var(--space-2);
    font-size: 12px;
    color: var(--danger);
  }
`

export default styles
