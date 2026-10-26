import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-af74a70-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-af74a70-row td {
    padding: var(--space-3) var(--space-2);
    border-bottom: 1px solid var(--border);
  }

  .s-af74a70-row:last-child td {
    border-bottom: none;
  }

  .s-af74a70-number {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  @media (max-width: 640px) {
    .s-af74a70-row td {
      padding: var(--space-2) var(--space-3);
      font-size: 14px;
    }
  }
`

export default styles
