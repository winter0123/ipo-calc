import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-8f009e6-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-8f009e6-container {
    --page-max: 640px;
  }

  /* 반응형 */
`

export default styles
