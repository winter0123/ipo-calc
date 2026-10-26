import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-4569b8b-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-4569b8b-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  /* 데스크톱: 할 일 · 다가오는 일정 2열 */
  @media (min-width: 1024px) {
    .s-4569b8b-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  .s-4569b8b-card {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }

  .s-4569b8b-title {
    margin: 0;
    font-size: 18px;
    color: var(--ink);
  }

  .s-4569b8b-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-4569b8b-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2) var(--space-3);
    padding: var(--space-2) var(--space-3);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--ink);
    overflow-wrap: anywhere;
  }

  .s-4569b8b-row a,
  .s-4569b8b-more {
    color: var(--link);
    font-size: 14px;
  }

  .s-4569b8b-muted {
    margin: 0;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-4569b8b-error {
    margin: 0;
    padding: var(--space-3);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--danger);
  }
`

export default styles
