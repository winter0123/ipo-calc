import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-f494095-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-f494095-checkboxLabel {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 14px;
    font-weight: 500;
    color: var(--ink);
    cursor: pointer;
  }

  .s-f494095-checkboxLabel input[type='checkbox'] {
    width: 18px;

    accent-color: var(--link);
  }

  .s-f494095-hint {
    font-size: 12px;
    color: var(--ink-muted);
    margin-top: var(--space-1);
  }

  .s-f494095-loading,
  .s-f494095-error {
    padding: var(--space-4);
    text-align: center;
    color: var(--ink-muted);
  }

  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-f494095-form,
  .s-f494095-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-f494095-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-f494095-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-f494095-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-f494095-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-f494095-submitButton {
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

  .s-f494095-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-f494095-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }
`

export default styles
