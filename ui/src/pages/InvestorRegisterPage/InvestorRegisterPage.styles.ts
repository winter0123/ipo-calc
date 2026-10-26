import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-72962ac-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-72962ac-container {
    --page-max: 760px;
  }

  .s-72962ac-count {
    color: var(--ink-muted);
  }

  .s-72962ac-investorList {
    display: grid;
    gap: var(--space-2);
    margin: var(--space-4) 0 var(--space-6);
    padding: 0;
    list-style: none;
  }

  .s-72962ac-investorRow {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(80px, 0.5fr) minmax(130px, 0.7fr);
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-72962ac-investorName {
    font-weight: 650;
    overflow-wrap: anywhere;
  }

  @media (max-width: 520px) {
    .s-72962ac-investorRow {
      grid-template-columns: minmax(0, 1fr) auto;
    }

    .s-72962ac-investorRow > span:last-child {
      grid-column: 1 / -1;
    }
  }
`

export default styles
