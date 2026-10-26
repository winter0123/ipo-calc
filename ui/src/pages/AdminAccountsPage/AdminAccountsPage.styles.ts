import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-edb538d-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-edb538d-container {
    --page-max: 1000px;
  }
`

export default styles
