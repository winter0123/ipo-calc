import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-63ae5ae-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-63ae5ae-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    width: 100%;
    max-width: 480px;
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .s-63ae5ae-card {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    padding: var(--space-4);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background-color: var(--surface);
  }

  .s-63ae5ae-cardHeader {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2);
  }

  .s-63ae5ae-ipoName {
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .s-63ae5ae-badgeUnallocated,
  .s-63ae5ae-badgeAllocated {
    padding: var(--space-1) var(--space-2);
    border-radius: 999px;
    font-size: 12px;
    font-weight: 600;
    white-space: nowrap;
  }

  .s-63ae5ae-badgeUnallocated {
    background-color: var(--warning-bg);
    color: var(--warning-ink);
  }

  .s-63ae5ae-badgeAllocated {
    background-color: var(--success-bg);
    color: var(--success-ink);
  }

  .s-63ae5ae-cardBody {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    margin: 0;
  }

  .s-63ae5ae-cardRow {
    display: flex;
    justify-content: space-between;
    font-size: 14px;
    color: var(--ink-muted);
  }

  .s-63ae5ae-cardRow dt {
    color: var(--ink-muted);
  }

  .s-63ae5ae-cardRow dd {
    margin: 0;
    color: var(--ink);
  }

  .s-63ae5ae-allocateButton {
    align-self: flex-start;
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;
    cursor: pointer;
  }

  .s-63ae5ae-allocateButton:hover {
    background-color: var(--primary-hover);
  }

  .s-63ae5ae-buttonGroup {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
    margin-top: var(--space-2);
  }

  .s-63ae5ae-actionButton {
    flex: 1;
    min-width: 80px;
    padding: var(--space-2) var(--space-3);
    background-color: var(--primary);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-size: 13px;
    font-weight: 600;
    text-decoration: none;
    text-align: center;
    cursor: pointer;
    white-space: nowrap;
  }

  .s-63ae5ae-actionButton:hover {
    background-color: var(--primary-hover);
  }

  .s-63ae5ae-empty {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
  }

  @media (min-width: 640px) {
    .s-63ae5ae-list {
      max-width: 560px;
    }
  }

  @media (min-width: 768px) {
    .s-63ae5ae-list {
      max-width: 640px;
    }
  }

  @media (min-width: 1024px) {
    .s-63ae5ae-list {
      max-width: 720px;
    }
  }

  /* 휴대폰: 청약수량·청약일 / 배정·남은 수량을 2단으로, 라벨 위 값 아래 */
  @media (max-width: 640px) {
    .s-63ae5ae-cardBody {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-2) var(--space-3);
    }

    .s-63ae5ae-cardRow {
      flex-direction: column;
      gap: 2px;
      font-size: 13px;
    }

    .s-63ae5ae-cardRow dd {
      font-size: 14px;
      font-variant-numeric: tabular-nums;
    }
  }
`

export default styles
