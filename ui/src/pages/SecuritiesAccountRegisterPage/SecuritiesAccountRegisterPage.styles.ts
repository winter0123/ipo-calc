import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-c77121a-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-c77121a-container {
    --page-max: 640px;
  }
`

export default styles
