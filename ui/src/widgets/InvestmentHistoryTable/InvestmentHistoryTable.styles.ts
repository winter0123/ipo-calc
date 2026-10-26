import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-fb986ce-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-fb986ce-tableContainer {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-fb986ce-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-fb986ce-table th,
  .s-fb986ce-table td {
    padding: var(--space-2) var(--space-3);
    text-align: left;
    border-bottom: 1px solid var(--border);
  }

  .s-fb986ce-table th {
    background-color: var(--surface-soft);
    font-weight: 600;
    color: var(--ink);
  }

  .s-fb986ce-table tbody tr {
    cursor: pointer;
    transition: background-color 0.15s;
  }

  .s-fb986ce-table tbody tr:hover {
    background-color: var(--surface-soft);
  }

  .s-fb986ce-table tbody tr[aria-selected='true'] {
    background-color: var(--surface-sea);
  }

  .s-fb986ce-selectedRow {
    background-color: var(--surface-sea);
  }

  .s-fb986ce-loading {
    margin: 0;
    padding: var(--space-6);
    text-align: center;
    color: var(--ink-muted);
    font-size: 16px;
  }

  .s-fb986ce-error {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--danger);
  }

  .s-fb986ce-detailWrapper {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
  }

  /* 모바일 */
  @media (max-width: 640px) {
    .s-fb986ce-table {
      font-size: 12px;
    }

    .s-fb986ce-table th,
    .s-fb986ce-table td {
      padding: var(--space-2) var(--space-2);
    }

    .s-fb986ce-table thead {
      display: none;
    }

    .s-fb986ce-table tbody tr {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-2);
      padding: var(--space-2);
      border-bottom: 1px solid var(--border);
    }

    /* 짧은 값은 라벨 위·값 아래로 2단에 놓는다(이름·상태만 첫 줄 양끝). */
    .s-fb986ce-table tbody td,
    .s-fb986ce-table tbody td.s-fb986ce-numeric {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 0;
      text-align: left;
      border-bottom: 0;
      font-variant-numeric: tabular-nums;
      overflow-wrap: anywhere;
    }

    .s-fb986ce-table tbody td::before {
      content: attr(data-label);
      color: var(--ink-muted);
    }

    .s-fb986ce-table tbody td.s-fb986ce-name {
      grid-column: 1;
      grid-row: 1;
      font-size: 14px;
      font-weight: 600;
    }

    .s-fb986ce-table tbody td.s-fb986ce-status {
      grid-column: 2;
      grid-row: 1;
      align-items: flex-end;
      text-align: right;
    }

    .s-fb986ce-table tbody td.s-fb986ce-name::before,
    .s-fb986ce-table tbody td.s-fb986ce-status::before {
      content: none;
    }
  }

  /* 태블릿 */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-fb986ce-table {
      font-size: 14px;
    }
  }

  /* 데스크톱 */
  @media (min-width: 1024px) {
    .s-fb986ce-table {
      max-width: 100%;
    }
  }

  .s-fb986ce-table th.s-fb986ce-numeric,
  .s-fb986ce-table td.s-fb986ce-numeric {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
`

export default styles
