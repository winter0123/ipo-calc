import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-15d7f91-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-15d7f91-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    flex-wrap: wrap;
  }

  .s-15d7f91-announcer {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  .s-15d7f91-heading {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--ink);
    text-align: center;
  }

  .s-15d7f91-controls {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    flex-wrap: wrap;
    justify-content: center;
  }

  .s-15d7f91-inputGroup {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .s-15d7f91-label {
    font-size: 14px;
    color: var(--ink-muted);
    font-weight: 500;
  }

  .s-15d7f91-input {
    width: 100px;

    text-align: center;
  }

  .s-15d7f91-select {
    width: 100px;

    cursor: pointer;
  }

  .s-15d7f91-inputError {
    border-color: var(--danger);
  }

  .s-15d7f91-inputError:focus {
    border-color: var(--danger);
  }

  .s-15d7f91-error {
    font-size: 12px;
    color: var(--danger);
    white-space: nowrap;
  }

  .s-15d7f91-navButton {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--space-2) var(--space-3);
    background-color: var(--surface);
    color: var(--link);
    border: 1px solid var(--link);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition:
      background-color 0.2s,
      color 0.2s,
      border-color 0.2s;
    white-space: nowrap;
    min-width: 44px;
    min-height: 44px;
  }

  .s-15d7f91-navButton:hover:not(:disabled) {
    background-color: var(--primary-hover);
    color: var(--on-primary);
  }

  .s-15d7f91-navButton:focus:not(:disabled) {
    outline: none;
  }

  .s-15d7f91-navButton:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    color: var(--ink-muted);
    border-color: var(--border);
  }

  .s-15d7f91-button {
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border: none;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
    transition: background-color 0.2s;
    white-space: nowrap;
  }

  .s-15d7f91-button:hover {
    background-color: var(--primary-hover);
  }

  .s-15d7f91-button:focus {
    outline: none;
  }

  /* 모바일 */
  @media (max-width: 640px) {
    .s-15d7f91-container {
      width: 100%;
      padding: var(--space-3);
    }
    .s-15d7f91-controls {
      flex-direction: column;
      align-items: stretch;
      gap: var(--space-2);
    }
    .s-15d7f91-inputGroup {
      width: 100%;
    }
    .s-15d7f91-input,
    .s-15d7f91-select {
      width: 100%;
    }
    .s-15d7f91-navButton {
      width: 100%;
    }
    .s-15d7f91-button {
      width: 100%;
    }
    .s-15d7f91-heading {
      font-size: 18px;
    }
  }

  /* 태블릿 */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-15d7f91-container {
      width: 100%;
    }
    .s-15d7f91-controls {
      justify-content: center;
    }
  }

  /* 데스크톱 */
  @media (min-width: 1024px) {
    .s-15d7f91-container {
      width: auto;
    }
  }

  .s-15d7f91-hint {
    margin: 0 0 var(--space-2);
    color: var(--ink-muted);
    font-size: 13px;
  }
`

export default styles
