import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-bb6837d-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-bb6837d-loading,
  .s-bb6837d-blockError {
    padding: var(--space-5);
    text-align: center;
    color: var(--ink-muted);
  }

  .s-bb6837d-blockError {
    color: var(--danger);
    background-color: var(--danger-bg);
    border-radius: var(--radius-sm);
  }

  .s-bb6837d-referenceSection {
    padding: var(--space-3);
    background-color: var(--surface-soft);
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
  }

  /* 반응형 */

  /* 폼 공통 규칙: 라벨은 입력창 위, 필드 간격 24px, 제출 버튼은 왼쪽 정렬 */
  .s-bb6837d-form,
  .s-bb6837d-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    width: 100%;
    max-width: 520px;
  }

  .s-bb6837d-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-bb6837d-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-bb6837d-error {
    color: var(--danger);
    font-size: 13px;
  }

  .s-bb6837d-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    font-size: 14px;
  }

  .s-bb6837d-submitButton {
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

  .s-bb6837d-submitButton:hover:not(:disabled) {
    background: var(--primary-hover);
  }

  .s-bb6837d-submitButton:disabled {
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
    cursor: not-allowed;
  }

  .s-bb6837d-hint {
    font-size: 13px;
    line-height: 1.5;
    color: var(--ink-muted);
  }

  /* 최종수익금: 입력이 아니라 계산 결과라서 색 면으로 구분한다 */
  .s-bb6837d-summary {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    padding: var(--space-4);
    background: var(--surface-sea);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }

  .s-bb6837d-summaryLabel {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-bb6837d-summaryValue {
    font-size: 24px;
    font-weight: 800;
    color: var(--ink);
  }

  .s-bb6837d-help {
    margin-top: var(--space-1);
  }

  .s-bb6837d-help summary {
    cursor: pointer;
    color: var(--link);
  }

  /* 캡처로 채우기 */
  .s-bb6837d-capture {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-bb6837d-captureTitle {
    margin: 0;
    font-size: 15px;
  }

  .s-bb6837d-captureList {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .s-bb6837d-captureItem {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    width: 100%;
    padding: var(--space-3);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    color: var(--ink);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .s-bb6837d-captureItem em {
    font-size: 13px;
    font-style: normal;
    color: var(--danger);
  }

  .s-bb6837d-captureNotes {
    margin: 0;
    padding-left: var(--space-5);
    color: var(--ink);
    font-size: 13px;
  }

  .s-bb6837d-capturedBadge {
    align-self: flex-start;
    padding: 1px var(--space-2);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    color: var(--ink-muted);
    font-size: 12px;
  }
`

export default styles
