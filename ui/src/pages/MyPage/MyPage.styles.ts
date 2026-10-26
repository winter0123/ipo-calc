import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-d118d08-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-d118d08-container {
    --page-max: 640px;
  }

  .s-d118d08-description,
  .s-d118d08-note {
    color: var(--ink-muted);
  }

  .s-d118d08-investors {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    margin-top: var(--space-5);
  }

  .s-d118d08-investorsHeader {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
  }

  .s-d118d08-investorsHeader h2 {
    margin: 0;
    font-size: 1.2rem;
  }

  .s-d118d08-investorsHeader p {
    margin: var(--space-1) 0 0;
    color: var(--ink-muted);
  }

  .s-d118d08-investorActions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
  }

  .s-d118d08-addInvestorButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-4);
    border-radius: var(--radius-sm);
    background: var(--primary);
    color: var(--on-primary);
    font-weight: 650;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-d118d08-securitiesAccountButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-4);
    border: 1px solid var(--primary);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--primary);
    font-weight: 650;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-d118d08-securitiesAccountButton:hover {
    background: var(--surface-sea);
  }

  .s-d118d08-addInvestorButton:hover {
    background: var(--primary-hover);
  }

  .s-d118d08-investorList {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: var(--space-3);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .s-d118d08-investorRow {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    min-width: 0;
    min-height: 80px;
    padding: var(--space-3);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
  }

  .s-d118d08-investorAvatar {
    display: inline-flex;
    flex: 0 0 48px;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    overflow: hidden;
    border-radius: 50%;
    background: var(--surface-sea);
    color: var(--primary);
  }

  .s-d118d08-investorAvatar img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .s-d118d08-investorInfo {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    min-width: 0;
  }

  .s-d118d08-investorName {
    overflow: hidden;
    color: var(--ink);
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .s-d118d08-investorRelationship {
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-d118d08-profile {
    margin: var(--space-5) 0 0;
    border-top: 1px solid var(--border);
  }

  .s-d118d08-profile > div {
    display: grid;
    grid-template-columns: minmax(120px, 0.7fr) minmax(0, 1.3fr);
    gap: var(--space-3);
    padding: var(--space-4) var(--space-2);
    border-bottom: 1px solid var(--border);
  }

  .s-d118d08-profile dt {
    color: var(--ink-muted);
    font-weight: 650;
  }

  .s-d118d08-profile dd {
    margin: 0;
    overflow-wrap: anywhere;
  }

  .s-d118d08-note {
    margin-top: var(--space-4);
    font-size: 14px;
  }

  @media (max-width: 480px) {
    .s-d118d08-profile > div {
      grid-template-columns: 1fr;
      gap: var(--space-1);
    }

    .s-d118d08-investorsHeader {
      align-items: flex-start;
    }

    .s-d118d08-investorsHeader {
      flex-direction: column;
      align-items: stretch;
    }

    .s-d118d08-investorActions > a {
      flex: 1;
    }
  }
`

export default styles
