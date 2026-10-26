import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-80ade02-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-80ade02-section {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    margin-bottom: var(--space-5);
  }

  .s-80ade02-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
  }

  .s-80ade02-title {
    margin: 0;
  }

  .s-80ade02-units {
    display: flex;
    gap: var(--space-2);
  }

  .s-80ade02-unit,
  .s-80ade02-active {
    padding: var(--space-2) var(--space-4);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
  }

  .s-80ade02-unit {
    background-color: var(--surface);
    color: var(--link);
    border: 1px solid var(--border-strong);
  }

  .s-80ade02-active {
    background-color: var(--primary);
    color: var(--on-primary);
    border: 1px solid var(--primary);
  }

  .s-80ade02-hint {
    margin: 0;
    color: var(--ink-muted);
    font-size: 13px;
  }

  .s-80ade02-chart {
    width: 100%;
    min-width: 0;
  }

  .s-80ade02-loading {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-80ade02-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
    font-variant-numeric: tabular-nums;
  }

  .s-80ade02-table th,
  .s-80ade02-table td {
    padding: var(--space-2);
    border-bottom: 1px solid var(--border);
    text-align: left;
  }

  .s-80ade02-table thead th {
    background-color: var(--surface-soft);
    border-bottom: 2px solid var(--border);
  }

  .s-80ade02-table tfoot th,
  .s-80ade02-table tfoot td {
    font-weight: 650;
    background-color: var(--surface-soft);
  }

  .s-80ade02-table .s-80ade02-numeric {
    text-align: right;
  }

  @media (max-width: 640px) {
    .s-80ade02-table {
      font-size: 12px;
    }
  }

  .s-80ade02-table tbody th {
    background-color: transparent;
    font-weight: 600;
  }
`

export default styles
