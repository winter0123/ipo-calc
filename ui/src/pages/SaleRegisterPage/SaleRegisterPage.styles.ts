import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-3d5bdce-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-3d5bdce-container {
    --page-max: 640px;
  }
`

export default styles
