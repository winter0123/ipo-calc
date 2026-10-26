import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-5d8dbe3-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-5d8dbe3-page {
    max-width: 900px;
    margin: 0 auto;
  }
  .s-5d8dbe3-page > a {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    color: var(--link);
  }
  .s-5d8dbe3-page h1 {
    margin-top: 20px;
  }
  .s-5d8dbe3-period {
    color: var(--ink-muted);
    font-size: 14px;
  }
  .s-5d8dbe3-content {
    margin-top: 24px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.7;
  }
`

export default styles
