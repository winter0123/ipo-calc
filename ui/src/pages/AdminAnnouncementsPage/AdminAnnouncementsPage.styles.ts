import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ccbb7cb-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ccbb7cb-page {
    max-width: 1000px;
    margin: 0 auto;
  }
  .s-ccbb7cb-form {
    display: grid;
    gap: 14px;
    padding: 20px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }
  .s-ccbb7cb-form label {
    display: grid;
    gap: 6px;
    font-weight: 600;
  }
  .s-ccbb7cb-form input,
  .s-ccbb7cb-form textarea,
  .s-ccbb7cb-form select {
    width: 100%;
    min-height: 44px;
    padding: 9px 11px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font: inherit;
  }
  .s-ccbb7cb-form textarea {
    resize: vertical;
  }
  .s-ccbb7cb-dates {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .s-ccbb7cb-filter {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 20px;
  }
  .s-ccbb7cb-filter select {
    min-height: 44px;
    padding: 8px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    font: inherit;
  }
  .s-ccbb7cb-actions {
    display: flex;
    gap: 8px;
  }
  .s-ccbb7cb-actions button {
    min-height: 44px;
    padding: 8px 14px;
    font: inherit;
    cursor: pointer;
  }
  .s-ccbb7cb-paging {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin: 20px 0;
  }
  .s-ccbb7cb-paging button {
    min-height: 44px;
    padding: 8px 14px;
    font: inherit;
  }
  .s-ccbb7cb-list {
    display: grid;
    gap: 10px;
    padding: 0;
    list-style: none;
  }
  .s-ccbb7cb-list li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }
  .s-ccbb7cb-list li > div:first-child {
    display: grid;
    gap: 6px;
  }
  .s-ccbb7cb-list span {
    color: var(--ink-muted);
    font-size: 14px;
  }
  @media (max-width: 640px) {
    .s-ccbb7cb-dates {
      grid-template-columns: 1fr;
    }
    .s-ccbb7cb-list li {
      align-items: flex-start;
      flex-direction: column;
    }
  }
`

export default styles
