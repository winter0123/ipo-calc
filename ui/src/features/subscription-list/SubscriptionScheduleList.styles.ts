import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ee1412e-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ee1412e-list {
    display: grid;
    gap: var(--space-3);
    list-style: none;
    margin: var(--space-4) 0 0;
    padding: 0;
  }
  .s-ee1412e-card {
    display: grid;
    gap: var(--space-3);
    padding: var(--space-4);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--surface);
  }
  .s-ee1412e-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: var(--space-3);
  }
  .s-ee1412e-header h2,
  .s-ee1412e-header p,
  .s-ee1412e-subscription p {
    margin: 0;
  }
  .s-ee1412e-header h2 {
    font-size: 1.1rem;
  }
  .s-ee1412e-header p {
    color: var(--ink-muted);
  }
  .s-ee1412e-badge {
    align-self: flex-start;
    border-radius: 999px;
    background: var(--warning-bg);
    color: var(--warning-ink);
    padding: var(--space-1) var(--space-2);
    font-size: 0.85rem;
  }
  .s-ee1412e-details {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
    gap: var(--space-2) var(--space-4);
    margin: 0;
  }
  .s-ee1412e-details dt {
    color: var(--ink-muted);
    font-size: 0.85rem;
  }
  .s-ee1412e-details dd {
    margin: 0;
  }
  .s-ee1412e-subscription {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-2);
    padding-top: var(--space-2);
    border-top: 1px solid var(--border);
  }
  .s-ee1412e-action {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    padding: 0 var(--space-3);
    border-radius: var(--radius-sm);
    background: var(--primary);
    color: var(--on-primary);
    text-decoration: none;
    white-space: nowrap;
  }
  .s-ee1412e-empty {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
  }
  @media (max-width: 640px) {
    .s-ee1412e-header,
    .s-ee1412e-subscription {
      align-items: flex-start;
      flex-direction: column;
    }
  }
`

export default styles
