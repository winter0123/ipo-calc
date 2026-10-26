import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-66fd75d-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  /* 공모주 상세 페이지. 최대 폭만 제한하고 중앙 정렬은 상위 레이아웃이 담당한다. */
  .s-66fd75d-container {
    --page-max: 720px;
    max-width: var(--page-max);
  }

  .s-66fd75d-header {
    display: flex;
    flex-direction: column;
  }

  .s-66fd75d-meta {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    flex-wrap: wrap;
    margin-top: var(--space-3);
  }

  .s-66fd75d-company {
    font-size: 16px;
    color: var(--ink-muted);
    font-weight: 500;
  }

  .s-66fd75d-statusChip {
    display: inline-flex;
    align-items: center;
    padding: 4px var(--space-3);
    border-radius: var(--radius-sm);
    font-size: 13px;
    font-weight: 650;
    line-height: 1.4;
  }

  .s-66fd75d-statusScheduled {
    background: var(--surface-sea);
    color: var(--ink);
  }

  .s-66fd75d-statusInProgress {
    background: var(--warning-bg);
    color: var(--warning-ink);
  }

  .s-66fd75d-statusClosed {
    background: var(--surface-soft);
    color: var(--ink-muted);
  }

  .s-66fd75d-statusListed {
    background: var(--success-bg);
    color: var(--success-ink);
  }

  .s-66fd75d-statusUnknown {
    background: var(--surface-soft);
    color: var(--ink-muted);
  }

  .s-66fd75d-detailGrid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-4);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    padding: var(--space-5);
  }

  /* 휴대폰: 짧은 값은 2단, 주소·최대주주처럼 긴 값만 한 줄 전체 폭. */
  @media (max-width: 640px) {
    .s-66fd75d-detailGrid {
      gap: var(--space-4) var(--space-3);
      padding: var(--space-4);
    }

    .s-66fd75d-wide {
      grid-column: 1 / -1;
    }
  }

  .s-66fd75d-detailItem {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .s-66fd75d-detailItem dt {
    font-size: 13px;
    font-weight: 600;
    color: var(--ink-muted);
  }

  .s-66fd75d-detailItem dd {
    margin: 0;
    font-size: 15px;
    color: var(--ink);
    word-break: break-word;
  }

  .s-66fd75d-actions {
    display: flex;
    gap: var(--space-3);
    flex-wrap: wrap;
  }

  .s-66fd75d-primaryLink,
  .s-66fd75d-secondaryLink {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-5);
    border-radius: var(--radius-sm);
    font-family: inherit;
    font-size: 15px;
    font-weight: 650;
    line-height: 1.2;
    text-decoration: none;
    cursor: pointer;
    transition:
      background-color 0.15s,
      border-color 0.15s;
  }

  .s-66fd75d-primaryLink {
    background-color: var(--primary);
    color: var(--on-primary);
    border: 1px solid transparent;
  }

  .s-66fd75d-primaryLink:hover {
    background-color: var(--primary-hover);
  }

  .s-66fd75d-primaryLink:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px var(--surface),
      0 0 0 4px var(--link);
  }

  .s-66fd75d-secondaryLink {
    background-color: var(--surface);
    color: var(--ink);
    border: 1px solid var(--border-strong);
  }

  .s-66fd75d-secondaryLink:hover {
    background-color: var(--surface-soft);
  }

  .s-66fd75d-secondaryLink:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px var(--surface),
      0 0 0 4px var(--link);
  }

  .s-66fd75d-insightSection {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-66fd75d-insightTitle {
    margin: 0;
    font-size: 18px;
    color: var(--ink);
  }

  .s-66fd75d-insightNote {
    margin: 0;
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-66fd75d-insightNote a {
    color: var(--link);
  }

  .s-66fd75d-tableWrap {
    overflow-x: auto;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
  }

  .s-66fd75d-insightTable {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-66fd75d-insightTable th,
  .s-66fd75d-insightTable td {
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--border);
    text-align: right;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  .s-66fd75d-insightTable th:first-child {
    text-align: left;
  }

  .s-66fd75d-insightTable tbody th {
    background-color: transparent;
    font-weight: 600;
  }

  .s-66fd75d-insightTable thead th {
    font-size: 13px;
    color: var(--ink-muted);
    font-weight: 600;
  }

  .s-66fd75d-insightTable tbody tr:last-child th,
  .s-66fd75d-insightTable tbody tr:last-child td {
    border-bottom: none;
  }

  .s-66fd75d-negative {
    color: var(--loss); /* 기업 실적 음수도 국내 관례(손실 파랑) */
  }

  /* 기간 이름(예: 제12기(2026년06월말))이 길어 머리글은 줄바꿈을 허용한다. */
  .s-66fd75d-insightTable thead th {
    white-space: normal;
    min-width: 72px;
  }

  /* 유통가능 일정 카드: 휴대폰에서만 보이고, 그때 표는 숨긴다. */
  .s-66fd75d-lockupCards {
    display: none;
    list-style: none;
    margin: 0;
    padding: 0;
    gap: var(--space-3);
  }

  .s-66fd75d-lockupCard {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    padding: var(--space-3) var(--space-4);
  }

  .s-66fd75d-lockupHead {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    gap: var(--space-1) var(--space-3);
    font-size: 14px;
  }

  .s-66fd75d-lockupHead span {
    color: var(--ink-muted);
  }

  .s-66fd75d-lockupStats {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-3);
    margin: var(--space-3) 0 0;
    font-variant-numeric: tabular-nums;
  }

  .s-66fd75d-lockupStats dt {
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-66fd75d-lockupStats dd {
    margin: 0;
    font-weight: 600;
  }

  .s-66fd75d-lockupBar {
    height: 4px;
    margin-top: var(--space-2);
    border-radius: 2px;
    background: var(--border);
  }

  .s-66fd75d-lockupBar span {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: var(--primary);
  }

  @media (max-width: 640px) {
    .s-66fd75d-lockupCards {
      display: grid;
    }

    .s-66fd75d-desktopOnly {
      display: none;
    }
  }

  .s-66fd75d-periodList {
    margin: var(--space-2) 0 0;
    font-size: 14px;
    font-variant-numeric: tabular-nums;
  }

  .s-66fd75d-periodList div {
    display: flex;
    justify-content: space-between;
    gap: var(--space-3);
    padding: var(--space-1) 0;
  }

  .s-66fd75d-periodList dt {
    color: var(--ink-muted);
  }

  .s-66fd75d-periodList dd {
    margin: 0;
    font-weight: 600;
  }
`

export default styles
