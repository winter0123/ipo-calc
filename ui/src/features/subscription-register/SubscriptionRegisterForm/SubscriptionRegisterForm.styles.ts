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
    background-color: var(--danger-bg);
    border-radius: var(--radius-sm);
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

  .s-aeddfbc-field label,
  .s-aeddfbc-fieldLabel {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-aeddfbc-allocationTotal {
    font-size: 16px;
    font-weight: 700;
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

  .s-aeddfbc-staticValue {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    color: var(--ink);
    font-size: 15px;
  }

  .s-aeddfbc-guide {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-5);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }

  .s-aeddfbc-guide h2 {
    margin: 0;
    font-size: 17px;
    color: var(--ink);
  }

  .s-aeddfbc-guide p {
    margin: 0;
    color: var(--ink-muted);
    line-height: 1.6;
  }

  .s-aeddfbc-guide a {
    align-self: flex-start;
    color: var(--link);
    font-weight: 650;
  }

  .s-aeddfbc-dateRow {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .s-aeddfbc-dateButton {
    min-height: 44px;
    min-width: 44px;
    padding: 0 var(--space-4);
    background: var(--surface);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    color: var(--ink);
    font: inherit;
    font-size: 15px;
    font-weight: 650;
    cursor: pointer;
  }

  .s-aeddfbc-dateButton:hover:not(:disabled) {
    border-color: var(--primary);
  }

  .s-aeddfbc-dateButton[aria-pressed='true'] {
    background: var(--primary);
    border-color: var(--primary);
    color: var(--on-primary);
  }

  .s-aeddfbc-dateButton:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
  }

  .s-aeddfbc-dateButton:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  /* 섹션 스타일 */
  .s-aeddfbc-section {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-aeddfbc-sectionTitle {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
    color: var(--ink);
  }
`

export default styles
