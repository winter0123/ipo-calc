import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ee1b1ba-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ee1b1ba-page {
    max-width: 900px;
    margin: 0 auto;
  }
  .s-ee1b1ba-list {
    display: grid;
    gap: 12px;
    padding: 0;
    list-style: none;
  }
  .s-ee1b1ba-list a {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 18px;
    color: var(--ink);
    text-decoration: none;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
  }
  .s-ee1b1ba-list a:hover {
    border-color: var(--link);
  }
  .s-ee1b1ba-list span {
    color: var(--ink-muted);
    font-size: 14px;
  }
  .s-ee1b1ba-paging {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-top: 20px;
  }
  .s-ee1b1ba-paging button {
    min-height: 44px;
    padding: 8px 14px;
    font: inherit;
  }
`

export default styles
