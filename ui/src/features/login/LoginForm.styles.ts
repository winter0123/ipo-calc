import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-584d505-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-584d505-form,
  .s-584d505-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-584d505-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-584d505-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-584d505-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-584d505-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-584d505-submitButton {
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

  .s-584d505-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-584d505-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }

  .s-584d505-submitButton {
    align-self: stretch;
  }
`

export default styles
