import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-c3cd71f-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-c3cd71f-form,
  .s-c3cd71f-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-c3cd71f-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-c3cd71f-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-c3cd71f-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-c3cd71f-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-c3cd71f-submitButton {
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

  .s-c3cd71f-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-c3cd71f-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }

  .s-c3cd71f-submitButton {
    align-self: stretch;
  }

  .s-c3cd71f-switch {
    margin-top: var(--space-4);
    font-size: 14px;
    color: var(--ink-muted);
    text-align: center;
  }

  .s-c3cd71f-switch a {
    color: var(--link);
    text-decoration: underline;
  }

  .s-c3cd71f-switch a:hover {
    color: var(--link-hover);
  }
`

export default styles
