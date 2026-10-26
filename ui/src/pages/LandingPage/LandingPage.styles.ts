import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-f44f111-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-f44f111-page {
    word-break: keep-all;
  }

  .s-f44f111-page h1,
  .s-f44f111-page h2,
  .s-f44f111-page p,
  .s-f44f111-page dl,
  .s-f44f111-page dd,
  .s-f44f111-page ul {
    margin: 0;
  }

  .s-f44f111-page p {
    color: var(--ink-muted);
    line-height: 1.65;
  }

  .s-f44f111-band {
    padding: clamp(52px, 8vw, 96px) var(--gutter);
    background: var(--surface);
  }

  .s-f44f111-bandGray {
    background: var(--surface-soft);
  }

  .s-f44f111-hero {
    padding: clamp(58px, 9vw, 108px) var(--gutter) clamp(48px, 7vw, 84px);
    background:
      radial-gradient(ellipse at 75% 12%, rgba(171, 247, 238, 0.42), transparent 38%),
      linear-gradient(135deg, var(--surface-sea), var(--surface) 62%);
  }

  .s-f44f111-inner {
    max-width: 1100px;
    margin-inline: auto;
  }

  .s-f44f111-heroGrid,
  .s-f44f111-showcase {
    display: grid;
    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
    align-items: center;
    gap: var(--space-7);
  }

  .s-f44f111-heroText,
  .s-f44f111-showcaseText {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    align-items: flex-start;
  }

  .s-f44f111-page h1 {
    font-size: clamp(28px, 4.4vw, 42px);
    line-height: 1.25;
    letter-spacing: -0.02em;
    text-wrap: balance;
  }

  .s-f44f111-page h2 {
    font-size: clamp(22px, 2.6vw, 28px);
    line-height: 1.3;
    letter-spacing: -0.01em;
    text-wrap: balance;
  }

  .s-f44f111-heroText > p {
    font-size: 17px;
  }

  .s-f44f111-example {
    padding-left: var(--space-3);
    border-left: 3px solid var(--primary);
    color: var(--ink);
    font-size: 15px;
  }

  .s-f44f111-flip {
    grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  }

  .s-f44f111-flip .s-f44f111-showcaseText {
    order: 2;
  }

  .s-f44f111-shot {
    display: block;
    width: 100%;
    height: auto;
    border: 1px solid rgba(220, 229, 239, 0.95);
    border-radius: 18px;
    background: var(--surface);
    box-shadow: 0 20px 54px rgba(25, 54, 91, 0.12);
  }

  .s-f44f111-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-3);
    margin-top: var(--space-2);
  }

  .s-f44f111-primary,
  .s-f44f111-secondary {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 48px;
    padding: 0 var(--space-5);
    border-radius: var(--radius-sm);
    font-weight: 650;
    text-decoration: none;
  }

  .s-f44f111-primary {
    background: var(--primary);
    color: var(--on-primary);
    border: 1px solid var(--primary);
  }

  .s-f44f111-primary:hover {
    background: var(--primary-hover);
  }

  .s-f44f111-secondary {
    background: var(--surface);
    color: var(--ink);
    border: 1px solid var(--border-strong);
  }

  .s-f44f111-secondary:hover {
    background: var(--surface-sea);
  }

  .s-f44f111-primary:focus-visible,
  .s-f44f111-secondary:focus-visible {
    outline: 3px solid var(--link);
    outline-offset: 2px;
  }

  .s-f44f111-twoCol {
    display: grid;
    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
    gap: var(--space-7);
    align-items: start;
  }

  .s-f44f111-list {
    display: flex;
    flex-direction: column;
  }

  .s-f44f111-list > div {
    display: grid;
    grid-template-columns: 120px minmax(0, 1fr);
    gap: var(--space-4);
    padding: var(--space-4) 0;
    border-top: 1px solid var(--border);
  }

  .s-f44f111-list > div:last-child {
    border-bottom: 1px solid var(--border);
  }

  .s-f44f111-list dt {
    color: var(--ink);
    font-weight: 700;
  }

  .s-f44f111-list dd {
    color: var(--ink-muted);
    line-height: 1.6;
  }

  .s-f44f111-notes {
    padding: 0;
    list-style: none;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-5) var(--space-6);
  }

  .s-f44f111-notes li {
    color: var(--ink);
    line-height: 1.6;
  }

  .s-f44f111-closing {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-4);
  }

  @media (max-width: 900px) {
    .s-f44f111-band,
    .s-f44f111-hero {
      padding: var(--space-7) var(--gutter);
    }

    .s-f44f111-heroGrid,
    .s-f44f111-showcase,
    .s-f44f111-twoCol {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--space-5);
    }

    .s-f44f111-flip {
      grid-template-columns: minmax(0, 1fr);
    }

    .s-f44f111-flip .s-f44f111-showcaseText {
      order: 0;
    }

    .s-f44f111-notes {
      grid-template-columns: minmax(0, 1fr);
    }

    .s-f44f111-list > div {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--space-1);
    }

    .s-f44f111-actions > * {
      flex: 1 1 100%;
    }
  }
`

export default styles
