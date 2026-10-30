import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-aud-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-aud-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
  }
  .s-aud-header {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }
  .s-aud-title {
    font-size: 20px;
    font-weight: 740;
  }
  .s-aud-tabs {
    display: flex;
    gap: 0;
    border-bottom: 1px solid var(--border);
  }
  .s-aud-tab {
    min-height: 44px;
    padding: 0 var(--space-5);
    border: 0;
    border-bottom: 2px solid transparent;
    background: none;
    color: var(--ink-muted);
    font: inherit;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
  }
  .s-aud-tab:hover {
    color: var(--ink);
  }
  .s-aud-tabActive {
    color: var(--primary);
    border-bottom-color: var(--primary);
  }
  .s-aud-infoTable {
    width: 100%;
  }
  .s-aud-infoTable th,
  .s-aud-infoTable td {
    padding: var(--space-2) var(--space-4);
    border-bottom: 1px solid var(--border);
    text-align: left;
  }
  .s-aud-infoTable th {
    width: 140px;
    font-size: 13px;
    font-weight: 600;
    color: var(--ink-muted);
    background: var(--surface-sea);
    white-space: nowrap;
  }
  .s-aud-sectionTitle {
    font-size: 16px;
    font-weight: 700;
    margin-top: var(--space-4);
  }
  .s-aud-historyTable {
    width: 100%;
  }
  .s-aud-historyTable th,
  .s-aud-historyTable td {
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--border);
    text-align: left;
  }
  .s-aud-historyTable th {
    font-size: 13px;
    font-weight: 650;
    color: var(--ink);
    background: var(--surface-sea);
    white-space: nowrap;
  }
  .s-aud-statsGrid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
    gap: var(--space-4);
  }
  .s-aud-statCard {
    padding: var(--space-5);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }
  .s-aud-statLabel {
    font-size: 13px;
    color: var(--ink-muted);
  }
  .s-aud-statValue {
    font-size: 20px;
    font-weight: 800;
    color: var(--ink);
  }
  .s-aud-status,
  .s-aul-error,
  .s-aud-empty {
    font-size: 14px;
  }
  .s-aud-status {
    color: var(--ink-muted);
  }
  .s-aud-error {
    color: var(--danger);
  }
  .s-aud-empty {
    color: var(--ink-muted);
    text-align: center;
    padding: var(--space-6);
  }
`

export default styles
