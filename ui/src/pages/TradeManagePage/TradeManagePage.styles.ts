import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-7b1c2d4-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-7b1c2d4-container {
    --page-max: 960px;
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-7b1c2d4-header {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-7b1c2d4-title {
    margin: 0;
    font-size: clamp(1.3rem, 2.4vw, 1.7rem);
    font-weight: 800;
    letter-spacing: -0.03em;
    color: var(--ink);
  }

  .s-7b1c2d4-ipoName {
    margin: 0;
    font-size: clamp(1.05rem, 2vw, 1.25rem);
    font-weight: 750;
    color: var(--primary-deep);
  }

  .s-7b1c2d4-meta {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2) var(--space-4);
    margin: 0;
    padding: 0;
    list-style: none;
    color: var(--ink-muted);
    font-size: 0.92rem;
  }

  .s-7b1c2d4-meta strong {
    color: var(--ink);
    font-weight: 700;
  }

  .s-7b1c2d4-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    max-width: 480px;
  }

  .s-7b1c2d4-field label {
    font-size: 0.92rem;
    font-weight: 650;
    color: var(--ink);
  }

  .s-7b1c2d4-field select {
    min-height: 44px;
    padding: 0 var(--space-3);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font-size: 1rem;
  }

  .s-7b1c2d4-field select:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
  }

  .s-7b1c2d4-hint {
    margin: 0;
    color: var(--ink-muted);
    font-size: 0.85rem;
  }

  .s-7b1c2d4-error {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 0.9rem;
    color: var(--danger);
  }

  .s-7b1c2d4-guide {
    padding: var(--space-6);
    text-align: center;
    color: var(--ink-muted);
    background-color: var(--surface-soft);
    border: 1px dashed var(--border);
    border-radius: var(--radius-sm);
  }

  .s-7b1c2d4-guide a {
    display: inline-block;
    margin-top: var(--space-3);
    color: var(--primary);
    font-weight: 650;
  }

  .s-7b1c2d4-retryButton {
    align-self: flex-start;
    min-height: 44px;
    margin-top: var(--space-2);
    padding: 0 var(--space-4);
    border: 1px solid var(--primary);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--primary);
    font-size: 0.92rem;
    font-weight: 650;
    cursor: pointer;
  }

  .s-7b1c2d4-retryButton:hover {
    background: var(--surface-soft);
  }

  .s-7b1c2d4-retryButton:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
  }

  .s-7b1c2d4-sections {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  @media (min-width: 1024px) {
    .s-7b1c2d4-sections {
      grid-template-columns: repeat(3, minmax(0, 1fr));
      align-items: start;
    }
  }

  .s-7b1c2d4-sectionBody {
    margin: var(--space-2) 0 0;
    color: var(--ink-muted);
    font-size: 0.92rem;
    line-height: 1.55;
  }

  .s-7b1c2d4-sectionMeta {
    margin: var(--space-2) 0 0;
    color: var(--ink);
    font-size: 0.88rem;
  }
`

export default styles
