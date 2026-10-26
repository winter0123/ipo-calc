import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-fce11dd-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-fce11dd-overlay {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: grid;
    place-items: center;
    padding: 20px;
    background: rgb(15 23 42 / 55%);
  }
  .s-fce11dd-dialog {
    width: min(100%, 560px);
    max-height: min(80vh, 720px);
    overflow: auto;
    padding: 24px;
    background: var(--surface);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-pop);
  }
  .s-fce11dd-kicker {
    margin: 0 0 8px;
    color: var(--link);
    font-weight: 700;
  }
  .s-fce11dd-titleButton {
    display: block;
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
    text-align: left;
    cursor: pointer;
  }
  .s-fce11dd-titleButton span {
    font-size: 22px;
    font-weight: 700;
  }
  .s-fce11dd-content {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.65;
  }
  .s-fce11dd-detail {
    min-height: 44px;
    padding: 6px 0;
    border: 0;
    background: none;
    color: var(--link);
    text-decoration: underline;
    font: inherit;
    cursor: pointer;
  }
  .s-fce11dd-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;
  }
  .s-fce11dd-actions button {
    min-height: 44px;
    padding: 8px 14px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    cursor: pointer;
  }
  .s-fce11dd-actions button:last-child {
    border-color: var(--link);
    background: var(--link);
    color: white;
  }
  @media (max-width: 640px) {
    .s-fce11dd-overlay {
      padding: 0;
    }
    .s-fce11dd-dialog {
      width: 100%;
      height: 100%;
      max-height: none;
      padding: 24px 20px calc(24px + env(safe-area-inset-bottom));
      border-radius: 0;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .s-fce11dd-actions {
      margin-top: auto;
    }
    .s-fce11dd-actions button {
      flex: 1;
    }
  }
`

export default styles
