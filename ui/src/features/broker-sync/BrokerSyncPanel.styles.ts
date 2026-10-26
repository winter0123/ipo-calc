import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-456ce0b-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-456ce0b-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-456ce0b-subtitle {
    margin: 0 0 var(--space-2);
    font-size: 16px;
  }

  .s-456ce0b-notice {
    margin: 0;
    padding: var(--space-3) var(--space-4);
    background: var(--info-bg);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--ink);
  }

  .s-456ce0b-error {
    color: var(--danger);
    font-size: 14px;
  }

  .s-456ce0b-list {
    margin: 0;
    padding-left: var(--space-4);
    font-size: 14px;
  }

  .s-456ce0b-tableWrap {
    overflow-x: auto;
    margin-bottom: var(--space-3);
  }

  .s-456ce0b-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
  }

  .s-456ce0b-table caption {
    text-align: left;
    padding-bottom: var(--space-2);
    font-weight: 500;
  }

  .s-456ce0b-table th,
  .s-456ce0b-table td {
    padding: var(--space-2) var(--space-2);
    border-bottom: 1px solid var(--border);
    text-align: left;
    white-space: nowrap;
  }

  .s-456ce0b-table input[type='text'] {
    width: 96px;
  }

  .s-456ce0b-table input[type='checkbox'] {
    width: 24px;
  }

  .s-456ce0b-range {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 240px));
    gap: var(--space-4);
  }

  .s-456ce0b-rangeField {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  @media (max-width: 640px) {
    .s-456ce0b-range {
      grid-template-columns: 1fr;
    }
  }
`

export default styles
