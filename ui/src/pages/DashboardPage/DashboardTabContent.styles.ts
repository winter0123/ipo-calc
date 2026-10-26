import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-3d5b89e-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-3d5b89e-dataArea {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }

  .s-3d5b89e-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .s-3d5b89e-list li {
    padding: var(--space-3) var(--space-4);
    background-color: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    color: var(--ink);
  }

  .s-3d5b89e-section {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-3d5b89e-sectionTitle {
    margin: 0;
    font-size: 18px;
    color: var(--ink);
  }

  .s-3d5b89e-noSales {
    margin: 0;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-3d5b89e-investors {
    display: flex;
    flex-direction: column;
  }

  .s-3d5b89e-investors[open] > .s-3d5b89e-investorsSummary {
    margin-bottom: var(--space-6);
  }

  .s-3d5b89e-investors > section + section {
    margin-top: var(--space-6);
  }

  .s-3d5b89e-investorsSummary {
    cursor: pointer;
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }
`

export default styles
