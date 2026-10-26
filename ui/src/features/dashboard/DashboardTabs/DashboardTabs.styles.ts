import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-a9c8657-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-a9c8657-tabList {
    display: flex;
    gap: var(--space-5);
    overflow-x: auto;
    border-bottom: 1px solid var(--border);
    scrollbar-width: none;
  }

  .s-a9c8657-tabList::-webkit-scrollbar {
    display: none;
  }

  .s-a9c8657-tab {
    flex-shrink: 0;
    min-height: 44px;
    padding: 0 var(--space-1);
    margin-bottom: -1px;
    background: none;
    border: none;
    border-bottom: 3px solid transparent;
    font: inherit;
    font-size: 15px;
    font-weight: 650;
    color: var(--ink-muted);
    cursor: pointer;
    white-space: nowrap;
  }

  .s-a9c8657-tab:hover {
    color: var(--ink);
  }

  .s-a9c8657-activeTab {
    color: var(--ink);
    border-bottom-color: var(--primary);
  }
`

export default styles
