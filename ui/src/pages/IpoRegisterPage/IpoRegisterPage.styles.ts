import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-7df5639-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-7df5639-container {
    --page-max: 520px;
  }

  .s-7df5639-description {
    margin: 0;
    color: var(--ink-muted);
    font-size: 14px;
  }
`

export default styles
