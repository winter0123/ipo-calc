import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-518086c-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-518086c-page {
    --page-max: 560px;
  }
`

export default styles
