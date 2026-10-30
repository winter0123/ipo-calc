import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-aulp-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-aulp-container {
    max-width: var(--content-max);
    margin: 0 auto;
    padding: var(--space-6) var(--gutter) var(--space-8);
  }
  .s-aulp-title {
    font-size: 24px;
    font-weight: 780;
    margin-bottom: var(--space-4);
  }
`

export default styles
