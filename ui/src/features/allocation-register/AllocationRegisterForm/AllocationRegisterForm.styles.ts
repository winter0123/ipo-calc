import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-e642165-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-e642165-loading,
  .s-e642165-blockError {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
  }

  .s-e642165-blockError {
    color: var(--danger);
    background-color: var(--danger-bg);
    border-radius: var(--radius-sm);
  }

  .s-e642165-referenceSection {
    padding: var(--space-3);
    background-color: var(--surface-soft);
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
  }

  /* 반응형 */

  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-e642165-form,
  .s-e642165-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-e642165-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-e642165-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-e642165-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-e642165-hint {
    color: var(--ink-muted);
    font-size: 13px;
  }

  .s-e642165-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-e642165-submitButton {
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

  .s-e642165-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-e642165-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }
`

export default styles
