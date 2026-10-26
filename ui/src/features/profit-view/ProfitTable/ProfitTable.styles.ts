import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-aaead19-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-aaead19-tableWrapper {
    overflow-x: auto;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-aaead19-table {
    width: 100%;
    border-collapse: collapse;
    min-width: 760px;
    font-size: 14px;
  }

  .s-aaead19-table th {
    background-color: var(--surface-soft);
    padding: var(--space-3) var(--space-2);
    text-align: left;
    font-weight: 600;
    color: var(--ink);
    border-bottom: 2px solid var(--border);
    white-space: nowrap;
  }

  th.s-aaead19-numeric {
    text-align: right;
  }

  .s-aaead19-empty {
    text-align: center;
    padding: var(--space-5);
    color: var(--ink-muted);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  @media (max-width: 640px) {
    .s-aaead19-table {
      font-size: 12px;
      min-width: 720px;
    }

    .s-aaead19-table th,
    .s-aaead19-table td {
      padding: var(--space-2) var(--space-2);
    }
  }

  @media (min-width: 768px) {
    .s-aaead19-table {
      min-width: 760px;
    }
  }

  @media (min-width: 1024px) {
    .s-aaead19-table {
      min-width: 760px;
    }
  }

  .s-aaead19-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: var(--space-3);
    margin: 0 0 var(--space-4);
  }

  .s-aaead19-card {
    padding: var(--space-3) var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-aaead19-cardLabel {
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-aaead19-cardValue {
    margin-top: var(--space-1);
    font-size: 20px;
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }

  .s-aaead19-emptyLink {
    display: inline-block;
    margin-top: var(--space-3);
    color: var(--link);
    font-weight: 650;
  }

  /* 휴대폰: 요약 카드 3개를 한 줄에 */
  @media (max-width: 640px) {
    .s-aaead19-cards {
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: var(--space-2);
    }

    .s-aaead19-card {
      padding: var(--space-2);
    }

    .s-aaead19-cardLabel {
      font-size: 12px;
    }

    .s-aaead19-cardValue {
      font-size: 14px;
      overflow-wrap: anywhere;
    }
  }

  .s-aaead19-emptyHint {
    margin: var(--space-2) 0;
    font-size: 14px;
  }

  /* 휴대폰: 매도 한 건을 카드로 (투자 내역 표와 같은 data-label 방식) */
  @media (max-width: 640px) {
    .s-aaead19-tableWrapper {
      overflow-x: visible;
      border: 0;
    }

    .s-aaead19-table {
      min-width: 0;
    }

    .s-aaead19-table thead {
      display: none;
    }

    .s-aaead19-table tbody tr {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      margin-bottom: var(--space-3);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }

    /* 라벨 위·값 아래 2단. 마지막(최종수익금)과 합계 칸은 한 줄 전체 폭. */
    .s-aaead19-table tbody td {
      display: flex;
      flex-direction: column;
      gap: 2px;
      text-align: left;
      overflow-wrap: anywhere;
    }

    .s-aaead19-table tbody td:nth-child(9),
    .s-aaead19-table tbody td[colspan] {
      grid-column: 1 / -1;
    }

    .s-aaead19-table tbody td::before {
      content: attr(data-label);
      color: var(--ink-muted);
      font-size: 12px;
      font-weight: 400;
    }
  }
`

export default styles
