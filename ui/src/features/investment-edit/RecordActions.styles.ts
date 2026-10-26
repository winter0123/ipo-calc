import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-428e173-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-428e173-actions {
    display: inline-flex;
    gap: var(--space-2);
  }

  .s-428e173-smallButton {
    min-height: 44px;
    padding: 0 var(--space-3);
    font-size: 13px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    cursor: pointer;
  }

  .s-428e173-smallButton:hover {
    background: var(--surface-soft);
  }

  .s-428e173-danger {
    color: var(--danger);
  }

  .s-428e173-error {
    margin: var(--space-1) 0 0;
    color: var(--danger);
    font-size: 13px;
  }

  .s-428e173-overlay {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--space-4);
    background: rgb(11 42 74 / 45%);
  }

  .s-428e173-dialog {
    width: 100%;
    max-width: 440px;
    max-height: 90vh;
    overflow-y: auto;
    padding: var(--space-5);
    background: var(--surface);
    border-radius: var(--radius-lg);
  }

  .s-428e173-dialogTitle {
    margin: 0 0 var(--space-4);
    font-size: 18px;
    color: var(--ink);
  }

  .s-428e173-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-428e173-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .s-428e173-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-428e173-fieldError {
    color: var(--danger);
    font-size: 13px;
  }

  .s-428e173-formActions {
    display: flex;
    gap: var(--space-2);
    justify-content: flex-end;
  }
`

export default styles
