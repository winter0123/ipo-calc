import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-sec-logo-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-sec-logo-logo {
    display: inline-block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    object-position: center;
    background: var(--surface);
    border-radius: var(--radius-sm);
    vertical-align: middle;
  }
`

export default styles