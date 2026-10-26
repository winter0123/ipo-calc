import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  { get: (_target, key) => (typeof key === 'string' ? 's-ae6612b-' + key : undefined) },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ae6612b-container {
    margin: 0;
    color: var(--ink);
    font-size: 14px;
  }

  .s-ae6612b-container span {
    color: var(--ink-muted);
  }
`

export default styles
