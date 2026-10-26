import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ef1f344-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-ef1f344-container {
    --page-max: 1200px;
  }

  .s-ef1f344-resultsArea {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-ef1f344-loading {
    margin: 0;
    padding: var(--space-6);
    text-align: center;
    color: var(--ink-muted);
    font-size: 16px;
  }

  .s-ef1f344-error {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--danger);
  }

  .s-ef1f344-emptyInvestors {
    padding: var(--space-7) var(--space-5);
    text-align: center;
    background-color: var(--surface-soft);
    border: 1px dashed var(--border);
    border-radius: var(--radius-sm);
  }

  .s-ef1f344-emptyMessage {
    margin: 0 0 var(--space-4);
    font-size: 16px;
    color: var(--ink-muted);
  }

  .s-ef1f344-registerLink {
    display: inline-block;
    padding: var(--space-2) var(--space-4);
    background-color: var(--primary);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;
    transition: background-color 0.2s;
  }

  .s-ef1f344-registerLink:hover {
    background-color: var(--primary-hover);
  }

  .s-ef1f344-registerLink:focus {
    outline: none;
  }

  .s-ef1f344-placeholder {
    padding: var(--space-6);
    text-align: center;
    color: var(--ink-muted);
    background-color: var(--surface-soft);
    border: 1px dashed var(--border);
    border-radius: var(--radius-sm);
    font-size: 16px;
  }

  /* 모바일 */
  @media (max-width: 640px) {
    .s-ef1f344-loading {
      padding: var(--space-5) var(--space-4);
    }
  }

  /* 태블릿 */
  /* 데스크톱 */

  .s-ef1f344-rowHint {
    margin: 0 0 var(--space-2);
    font-size: 13px;
    color: var(--ink-muted);
  }
`

export default styles
