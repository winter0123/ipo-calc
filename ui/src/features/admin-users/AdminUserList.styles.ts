import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-aul-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-aul-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
  }
  .s-aul-title {
    font-size: 20px;
    font-weight: 740;
  }
  .s-aul-searchForm {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }
  .s-aul-searchRow {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
    align-items: flex-start;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--space-4);
    background: var(--surface);
  }
  .s-aul-searchLabel,
  .s-aul-selectLabel {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    font-size: 13px;
    font-weight: 600;
    color: var(--ink-muted);
    min-width: 120px;
  }
  .s-aul-searchInput {
    min-width: 170px;
  }
  .s-aul-select {
    min-width: 110px;
  }
  .s-aul-status,
  .s-aul-error,
  .s-aul-empty {
    font-size: 14px;
  }
  .s-aul-status {
    color: var(--ink-muted);
  }
  .s-aul-error {
    color: var(--danger);
  }
  .s-aul-empty {
    color: var(--ink-muted);
    text-align: center;
    padding: var(--space-6);
  }
  .s-aul-toolbar {
    display: flex;
    gap: var(--space-4);
    align-items: center;
    flex-wrap: wrap;
    font-size: 13px;
  }
  .s-aul-sizeLabel,
  .s-aul-sortLabel {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-weight: 600;
    color: var(--ink-muted);
  }
  .s-aul-sizeSelect,
  .s-aul-sortSelect {
    min-width: 130px;
  }
  .s-aul-table {
    width: 100%;
  }
  .s-aul-table caption {
    font-size: 13px;
    color: var(--ink-muted);
    margin-bottom: var(--space-2);
    text-align: left;
  }
  .s-aul-paging {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 13px;
  }
  .s-aul-pagingInfo {
    color: var(--ink-muted);
  }
`

export default styles
