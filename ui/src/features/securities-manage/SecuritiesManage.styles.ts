import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ed0b413-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ed0b413-firms {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  /* 목록은 1열 기본, min-width 1024px에서 정확히 2열(auto-fill 금지)
     align-items: start — 행 높이가 가장 큰 카드에 맞춰 다른 카드가 늘어나지 않게 */
  .s-ed0b413-firms {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-2);
    align-items: start;
  }

  @media (min-width: 1024px) {
    .s-ed0b413-firms {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  .s-ed0b413-firm {
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 4px 8px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .s-ed0b413-firmHead,
  .s-ed0b413-numbers {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  /* 번호들은 필요한 만큼 작은 줄로 접힌다(단일 계좌는 한 줄 유지) */
  .s-ed0b413-numbers {
    gap: 4px;
    min-width: 0;
    flex: 0 1 auto;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .s-ed0b413-numbers .s-ed0b413-formError {
    flex-basis: 100%;
    margin-bottom: 0;
  }

  .s-ed0b413-firmHead {
    /* 기본 한 줄 유지: 초장 회사명(firmName)만 ellipsis로 유연하게 줄어든다 */
    flex-wrap: nowrap;
    min-width: 0;
    gap: 4px;
  }

  .s-ed0b413-firmName {
    font-size: 15px;
    margin: 0;
    margin-right: auto;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    min-width: 0;
    flex: 0 1 auto;
  }

  .s-ed0b413-firmLogo {
    flex-shrink: 0;
    margin-right: 2px;
  }

  .s-ed0b413-firmCheckbox {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--space-2);
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
    min-height: 32px;
    min-width: 32px;
    flex-shrink: 0;
  }

  .s-ed0b413-firmCheckbox input {
    min-width: 20px;
    min-height: 20px;
    cursor: pointer;
  }

  .s-ed0b413-firmCheckbox input:disabled {
    cursor: not-allowed;
  }

  .s-ed0b413-number {
    font-family: var(--mono);
    font-size: 13px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
  }

  /* 계좌번호 자체가 마스킹↔평문 토글 버튼이다(별도 보기 버튼 없음) */
  .s-ed0b413-numberButton {
    font-family: var(--mono);
    font-size: 13px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    background: transparent;
    border: none;
    border-radius: var(--radius-sm);
    padding: 0 2px;
    min-height: 32px;
    min-width: 32px;
    display: inline-flex;
    align-items: center;
    cursor: pointer;
  }

  .s-ed0b413-numberButton:hover {
    background: var(--surface-soft);
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  .s-ed0b413-numberButton:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px var(--surface),
      0 0 0 4px var(--link);
  }

  .s-ed0b413-numberButton:disabled {
    cursor: progress;
    color: var(--ink-muted);
  }

  .s-ed0b413-numberNoneText {
    font-size: 13px;
    color: var(--ink-muted);
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 0 var(--space-2);
    min-height: 32px;
    display: inline-flex;
    align-items: center;
    white-space: nowrap;
  }

  /* 평소엔 "계좌관리" 토글 하나만 보이는 Compact 버튼 */
  .s-ed0b413-actionToggle {
    min-height: 32px;
    padding: 0 12px;
    font-size: 12px;
    font-weight: 650;
    border: 1px solid var(--border-strong);
    border-radius: 8px;
    background: var(--surface);
    color: var(--ink);
    cursor: pointer;
    white-space: nowrap;
    flex-shrink: 0;
  }

  .s-ed0b413-actionToggle:hover {
    background: var(--surface-soft);
  }

  .s-ed0b413-actionToggle[aria-expanded='true'] {
    background: var(--surface-sea);
    border-color: var(--link);
    color: var(--link);
  }

  .s-ed0b413-actionToggle:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px var(--surface),
      0 0 0 4px var(--link);
  }

  .s-ed0b413-actionToggle:disabled {
    cursor: not-allowed;
    color: var(--ink-muted);
    border-color: var(--border);
    background: var(--surface-soft);
  }

  /* 펼친 액션 패널(펼칠 때만 추가 높이 허용): 계좌별 한 행(대상 별칭/번호 + 수정·삭제) + 계좌추가 1행 */
  .s-ed0b413-actionGroup {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 2px;
    padding: 2px 0;
  }

  .s-ed0b413-actionRow {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
    min-width: 0;
  }

  .s-ed0b413-actionTarget {
    flex: 0 1 auto;
    min-width: 0;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-ed0b413-actionGroup button {
    min-height: 32px;
    padding: 0 12px;
    font-size: 12px;
    font-weight: 600;
    border: 1px solid var(--border-strong);
    border-radius: 8px;
    background: var(--surface);
    color: var(--ink);
    cursor: pointer;
    white-space: nowrap;
  }

  .s-ed0b413-actionGroup button:hover {
    background: var(--surface-soft);
  }

  .s-ed0b413-actionGroup button:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px var(--surface),
      0 0 0 4px var(--link);
  }

  .s-ed0b413-actionGroup button:disabled {
    cursor: not-allowed;
    color: var(--ink-muted);
    border-color: var(--border);
    background: var(--surface-soft);
  }

  .s-ed0b413-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    max-width: 420px;
    min-width: 0;
  }

  .s-ed0b413-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-ed0b413-field label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink);
  }

  .s-ed0b413-check {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 14px;
    min-height: 44px;
  }

  .s-ed0b413-fieldError {
    color: var(--danger);
    font-size: 13px;
  }

  .s-ed0b413-fieldHint {
    color: var(--ink-muted);
    font-size: 13px;
  }

  .s-ed0b413-fieldHintBlock {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-2);
  }

  .s-ed0b413-formError {
    padding: var(--space-3) var(--space-4);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    color: var(--danger);
    margin-bottom: var(--space-3);
  }

  .s-ed0b413-sectionTitle {
    font-size: 15px;
    color: var(--ink-muted);
    margin: var(--space-4) 0 var(--space-2);
  }
`

export default styles
