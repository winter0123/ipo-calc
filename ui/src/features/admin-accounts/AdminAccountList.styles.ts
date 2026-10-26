import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-894955d-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-894955d-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-894955d-title {
    margin: 0;
    font-size: 18px;
  }

  .s-894955d-filter {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    min-height: 44px;
  }

  .s-894955d-tableWrap {
    overflow-x: auto;
  }

  .s-894955d-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-894955d-table caption {
    text-align: left;
    padding-bottom: var(--space-2);
    font-weight: 500;
  }

  .s-894955d-table th,
  .s-894955d-table td {
    padding: var(--space-2) var(--space-2);
    border-bottom: 1px solid var(--border);
    text-align: left;
    white-space: nowrap;
  }
`

export default styles
