import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-2b6c9e3-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-2b6c9e3-container {
    --page-max: 720px;
  }
`

export default styles
