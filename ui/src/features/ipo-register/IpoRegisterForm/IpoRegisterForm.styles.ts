import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-30438e1-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  /* 반응형 */

  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-30438e1-form,
  .s-30438e1-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-30438e1-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-30438e1-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-30438e1-required {
    color: var(--danger);
    font-weight: 650;
  }

  .s-30438e1-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-30438e1-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-30438e1-submitButton {
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

  .s-30438e1-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-30438e1-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }

  input[type='text'],
  input[type='number'],
  input[type='date'],
  select {
    min-height: 44px;
    padding: var(--space-2) var(--space-3);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 15px;
    color: var(--ink);
    background: var(--surface);
    width: 100%;
    box-sizing: border-box;
  }

  input[type='text']:focus,
  input[type='number']:focus,
  input[type='date']:focus,
  select:focus {
    outline: 2px solid var(--link);
    outline-offset: -2px;
  }

  input[type='text'][aria-invalid='true'],
  input[type='number'][aria-invalid='true'],
  input[type='date'][aria-invalid='true'],
  select[aria-invalid='true'] {
    border-color: var(--danger);
  }
`

export default styles
