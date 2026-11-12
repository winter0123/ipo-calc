import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-7b1f0a2-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;

  .s-7b1f0a2-summaryBar {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3) var(--space-5);
    padding: var(--space-3) var(--space-4);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    color: var(--ink);
    font-size: 14px;
  }

  .s-7b1f0a2-summaryBar strong {
    font-variant-numeric: tabular-nums;
  }

  .s-7b1f0a2-list {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-7b1f0a2-list th,
  .s-7b1f0a2-list td {
    padding: var(--space-2);
    text-align: left;
    border-bottom: 1px solid var(--border);
  }

  .s-7b1f0a2-list th {
    background-color: var(--surface-soft);
    font-weight: 600;
  }

  .s-7b1f0a2-list td {
    font-variant-numeric: tabular-nums;
    overflow-wrap: anywhere;
  }

  .s-7b1f0a2-rowActions {
    display: flex;
    gap: var(--space-2);
  }

  .s-7b1f0a2-smallButton {
    min-height: 44px;
    min-width: 44px;
    padding: 0 var(--space-3);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    color: var(--ink);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
  }

  .s-7b1f0a2-smallButton:hover:not(:disabled) {
    background: var(--surface-soft);
  }

  .s-7b1f0a2-smallButton:disabled {
    color: var(--ink-muted);
    cursor: not-allowed;
  }

  .s-7b1f0a2-danger {
    color: var(--danger);
    border-color: var(--danger-border);
  }

  .s-7b1f0a2-statusCancelled {
    color: var(--ink-muted);
    font-size: 12px;
  }

  .s-7b1f0a2-editRow {
    background: var(--surface-soft);
  }

  .s-7b1f0a2-editGrid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: var(--space-3);
    padding: var(--space-3) 0;
  }

  .s-7b1f0a2-editField {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .s-7b1f0a2-editField label {
    font-size: 13px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-7b1f0a2-fieldError {
    color: var(--danger);
    font-size: 13px;
  }

  .s-7b1f0a2-fieldHint {
    color: var(--ink-muted);
    font-size: 13px;
    line-height: 1.5;
  }

  /* 좁은 화면: 표 대신 행마다 카드로 쌓는다 */
  @media (max-width: 640px) {
    .s-7b1f0a2-list thead {
      position: absolute;
      left: -9999px;
    }

    .s-7b1f0a2-list,
    .s-7b1f0a2-list tbody,
    .s-7b1f0a2-list tr,
    .s-7b1f0a2-list td {
      display: block;
    }

    .s-7b1f0a2-list td {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-1) 0;
      border-bottom: none;
    }

    .s-7b1f0a2-list td::before {
      content: attr(data-label);
      font-weight: 600;
    }

    .s-7b1f0a2-list tr {
      padding: var(--space-2) 0;
      border-bottom: 1px solid var(--border);
    }

    .s-7b1f0a2-editGrid {
      grid-template-columns: 1fr;
    }
  }
`

export default styles
