import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-aeddfbc-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-aeddfbc-loading,
  .s-aeddfbc-blockError {
    padding: var(--space-4);
    text-align: center;
    color: var(--ink-muted);
  }

  .s-aeddfbc-blockError {
    color: var(--danger);
  }

  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-aeddfbc-form,
  .s-aeddfbc-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-aeddfbc-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-aeddfbc-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-aeddfbc-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-aeddfbc-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-aeddfbc-submitButton {
    align-self: flex-start;
    min-height: 44px;
    min-width: 120px;
    padding: 0 var(--space-5);
    background: var(--primary);
    color: var(--on-primary);
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 15px;
    font-weight: 650;
    cursor: pointer;
    transition: background-color 0.15s;
  }

  .s-aeddfbc-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-aeddfbc-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }

  .s-aeddfbc-hint {
    font-size: 14px;
    color: var(--ink-muted);
  }

  .s-aeddfbc-ipoInfo {
    margin: 0;
    font-size: 13px;
    color: var(--ink-muted);
  }
`

export default styles
