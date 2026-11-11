import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-cf44966-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-cf44966-container {
    --page-max: 760px;
  }
`

export default styles
