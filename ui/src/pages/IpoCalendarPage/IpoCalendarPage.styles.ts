import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-2aa7737-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-2aa7737-container {
    --page-max: 1200px;
  }

  .s-2aa7737-loading {
    margin: 0;
    color: var(--ink-muted);
    text-align: center;
    padding: var(--space-7) 0;
  }

  .s-2aa7737-error {
    margin: 0;
    color: var(--danger);
    text-align: center;
    padding: var(--space-7) 0;
  }
`

export default styles
