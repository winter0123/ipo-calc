import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  { get: (_target, key) => (typeof key === 'string' ? 's-3a0b04a-' + key : undefined) },
)

export const StyleScope = styled.div`
  display: contents;
  .s-3a0b04a-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    margin: 0;
    color: var(--ink);
    font-size: 14px;
  }

  .s-3a0b04a-container span {
    color: var(--ink-muted);
  }
`

export default styles
