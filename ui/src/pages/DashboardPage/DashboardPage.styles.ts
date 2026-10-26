import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-472c41a-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-472c41a-container {
    --page-max: 1200px;
  }

  .s-472c41a-resultsArea {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-472c41a-loading {
    margin: 0;
    padding: var(--space-6);
    text-align: center;
    color: var(--ink-muted);
    font-size: 16px;
  }

  .s-472c41a-error {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background-color: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--danger);
  }

  .s-472c41a-navLink {
    margin-top: var(--space-4);
  }

  .s-472c41a-navLink a {
    color: var(--link);
    text-decoration: none;
    font-size: 14px;
  }

  .s-472c41a-navLink a:hover {
    text-decoration: underline;
  }
`

export default styles
