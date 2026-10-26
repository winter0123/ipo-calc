import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-84af248-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-84af248-container {
    --page-max: 1000px;
  }
`

export default styles
