import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-cf44966-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-cf44966-container {
    --page-max: 760px;
  }

  .s-cf44966-brokerSyncAction {
    display: flex;
    justify-content: flex-end;
    margin-top: var(--space-4);
    padding-top: var(--space-4);
    border-top: 1px solid var(--border);
  }

  .s-cf44966-brokerSyncButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-5);
    border-radius: var(--radius-sm);
    background: var(--primary);
    color: var(--on-primary);
    font-weight: 650;
    text-decoration: none;
  }

  .s-cf44966-brokerSyncButton:hover {
    background: var(--primary-hover);
  }
`

export default styles
