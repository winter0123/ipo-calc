import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-0c24289-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-0c24289-page {
    --page-max: 480px;
  }

  .s-0c24289-switch {
    margin-top: var(--space-4);
    font-size: 14px;
    color: var(--ink-muted);
    text-align: center;
  }

  .s-0c24289-switch a {
    color: var(--link);
    text-decoration: underline;
  }

  .s-0c24289-switch a:hover {
    color: var(--link-hover);
  }
`

export default styles
