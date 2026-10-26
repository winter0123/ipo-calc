import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-e6172c5-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-e6172c5-summaryRow {
    background-color: var(--surface-soft);
    font-weight: 600;
  }

  .s-e6172c5-summaryRow td {
    padding: var(--space-3) var(--space-2);
    border-top: 2px solid var(--border);
    border-bottom: none;
  }

  .s-e6172c5-label {
    text-align: right !important;
  }

  .s-e6172c5-number {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .s-e6172c5-totalSaleAmount {
    font-weight: 600;
  }

  @media (max-width: 640px) {
    .s-e6172c5-summaryRow td {
      padding: var(--space-2) var(--space-3);
      font-size: 14px;
      border-top: 0;
    }

    .s-e6172c5-summaryRow td:first-child {
      font-size: 15px;
    }

    .s-e6172c5-label {
      text-align: left !important;
    }
  }
`

export default styles
