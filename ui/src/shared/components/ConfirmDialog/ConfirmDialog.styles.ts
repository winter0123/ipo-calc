import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-97aa055-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-97aa055-overlay {
    position: fixed;
    inset: 0;
    background-color: rgba(27, 36, 48, 0.45);
    backdrop-filter: blur(3px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: var(--space-4);
  }

  .s-97aa055-dialog {
    background-color: var(--surface);
    border-radius: var(--radius-lg);
    padding: var(--space-5);
    max-width: 400px;
    width: 100%;
    box-shadow: var(--shadow-pop);
  }

  .s-97aa055-title {
    font-size: 18px;
    font-weight: 700;
    color: var(--ink);
    margin: 0 0 var(--space-3);
  }

  .s-97aa055-message {
    font-size: 16px;
    color: var(--ink-muted);
    margin: 0 0 var(--space-5);
    white-space: pre-wrap;
    line-height: 1.5;
  }
  .s-97aa055-message b {
    font-weight: 650;
    color: var(--ink);
  }

  .s-97aa055-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--space-2);
  }

  .s-97aa055-cancelButton,
  .s-97aa055-confirmButton {
    min-height: 44px;
    padding: var(--space-2) var(--space-4);
    border-radius: var(--radius-sm);
    font-size: 16px;
    font-weight: 650;
    font-family: inherit;
    cursor: pointer;
    border: 1px solid transparent;
  }

  .s-97aa055-cancelButton {
    background-color: var(--surface);
    color: var(--ink);
    border-color: var(--border-strong);
  }

  .s-97aa055-cancelButton:hover {
    background-color: var(--surface-sea);
  }

  .s-97aa055-confirmButton {
    background-color: var(--danger);
    color: #fff;
  }

  .s-97aa055-confirmButton:hover {
    background-color: #8f1d17;
  }

  @media (min-width: 640px) {
    .s-97aa055-dialog {
      max-width: 440px;
    }
  }
`

export default styles
