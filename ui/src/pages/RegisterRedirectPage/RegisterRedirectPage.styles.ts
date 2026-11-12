import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-3f9a1c2-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-3f9a1c2-container {
    --page-max: 640px;
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-3f9a1c2-title {
    margin: 0;
    font-size: clamp(1.3rem, 2.4vw, 1.7rem);
    font-weight: 800;
    letter-spacing: -0.03em;
    color: var(--ink);
  }

  .s-3f9a1c2-guide {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: clamp(20px, 3vw, 28px);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    color: var(--ink);
  }

  .s-3f9a1c2-guide p {
    margin: 0;
    color: var(--ink-muted);
    line-height: 1.6;
  }

  .s-3f9a1c2-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }
`

export default styles
