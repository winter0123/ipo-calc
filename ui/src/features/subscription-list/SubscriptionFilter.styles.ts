import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-941e3f4-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-941e3f4-group {
    display: flex;
    gap: var(--space-2);
    flex-wrap: wrap;
  }

  .s-941e3f4-button {
    padding: var(--space-2) var(--space-4);
    border: 1px solid var(--border);
    border-radius: 999px;
    background-color: var(--surface);
    color: var(--ink);
    font-size: 14px;
    font-family: inherit;
    cursor: pointer;
    transition:
      background-color 0.2s,
      border-color 0.2s,
      color 0.2s;
  }

  .s-941e3f4-button:hover {
    border-color: var(--primary);
  }

  .s-941e3f4-button[aria-pressed='true'] {
    background-color: var(--primary);
    border-color: var(--link);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
  }
`

export default styles
