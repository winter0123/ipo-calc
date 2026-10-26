import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-e368fae-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-e368fae-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-3);
  }

  .s-e368fae-title {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: var(--ink);
  }

  .s-e368fae-closeButton {
    background: none;
    border: none;
    font-size: 18px;
    cursor: pointer;
    padding: var(--space-1) var(--space-2);
    color: var(--ink-muted);
    border-radius: var(--radius-sm);
  }

  .s-e368fae-closeButton:hover {
    background-color: var(--surface-soft);
  }

  .s-e368fae-dl {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-e368fae-dlRow {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    font-size: 14px;
  }

  .s-e368fae-dlRow dt {
    font-size: 13px;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .s-e368fae-dlRow dd {
    margin: 0;
    font-size: 15px;
    color: var(--ink);
  }

  .s-e368fae-nestedGroup {
    background: var(--surface-soft);
    border-radius: var(--radius-sm);
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-e368fae-groupTitle {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .s-e368fae-salesTable {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-e368fae-salesTable th,
  .s-e368fae-salesTable td {
    padding: var(--space-2) var(--space-2);
    text-align: left;
    border-bottom: 1px solid var(--border);
  }

  .s-e368fae-salesTable th {
    background-color: var(--surface-soft);
    font-weight: 600;
  }

  /* 모바일 */
  @media (max-width: 640px) {
    .s-e368fae-detail {
      padding: var(--space-4);
    }

    .s-e368fae-dl {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-3);
    }

    .s-e368fae-dlRow dd {
      font-size: 14px;
      font-variant-numeric: tabular-nums;
      overflow-wrap: anywhere;
    }

    /* 좁은 화면: 표 대신 행마다 카드로 쌓아 관리 버튼까지 보이게 한다 */
    .s-e368fae-salesTable thead {
      position: absolute;
      left: -9999px;
    }

    .s-e368fae-salesTable,
    .s-e368fae-salesTable tbody,
    .s-e368fae-salesTable tr,
    .s-e368fae-salesTable td {
      display: block;
    }

    .s-e368fae-salesTable td {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-1) 0;
    }

    .s-e368fae-salesTable td::before {
      content: attr(data-label);
      font-weight: 600;
    }

    .s-e368fae-salesTable tr {
      padding: var(--space-2) 0;
      border-bottom: 1px solid var(--border);
    }

    .s-e368fae-salesTable td {
      border-bottom: none;
    }
  }

  /* 641px 이상: 2열 그리드 */
  @media (min-width: 641px) {
    .s-e368fae-dl {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-3) var(--space-5);
    }

    .s-e368fae-dlRow {
      flex-direction: column;
      gap: var(--space-1);
    }

    .s-e368fae-detail {
      padding: var(--space-5);
    }
  }

  /* 태블릿 */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-e368fae-detail {
      padding: var(--space-5);
    }
  }

  /* 데스크톱 */
  @media (min-width: 1024px) {
    .s-e368fae-detail {
      padding: var(--space-5);
    }
  }

  .s-e368fae-actionLink {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;
  }

  .s-e368fae-actionLink:hover {
    background-color: var(--primary-hover);
  }

  .s-e368fae-stack {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-e368fae-actionRow {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
  }
`

export default styles
