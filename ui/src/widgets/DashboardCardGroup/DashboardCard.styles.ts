import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-e6bee2f-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-e6bee2f-card {
    padding: var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    text-align: left;
  }

  .s-e6bee2f-label {
    font-size: 14px;
    color: var(--ink-muted);
    font-weight: 500;
  }

  .s-e6bee2f-value {
    font-size: 20px;
    font-weight: 700;
    color: var(--ink);
  }

  /* 모바일 */
  @media (max-width: 640px) {
    .s-e6bee2f-card {
      padding: var(--space-2) var(--space-3);
    }
    .s-e6bee2f-label {
      font-size: 13px;
    }
    .s-e6bee2f-value {
      font-size: 16px;
      overflow-wrap: anywhere;
    }
  }

  /* 데스크톱 */
  @media (min-width: 1024px) {
    .s-e6bee2f-card {
      padding: var(--space-4);
    }
    .s-e6bee2f-value {
      font-size: 24px;
    }
  }
`

export default styles
