import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-8b8326f-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-8b8326f-container {
    --page-max: 640px;
  }
`

export default styles
