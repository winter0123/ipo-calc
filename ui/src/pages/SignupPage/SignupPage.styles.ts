import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-520c339-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-520c339-page {
    --page-max: 480px;
  }
`

export default styles
