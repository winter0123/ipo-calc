import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-ccbb7cb-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;

  .s-ccbb7cb-pageWrapper {
    --page-max: 1000px;
    display: grid;
    gap: 20px;
  }

  /* 섹션 공통: 카드 */
  .s-ccbb7cb-section {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }

  /* 필터 섹션 */
  .s-ccbb7cb-filterSection {
    padding: 16px 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .s-ccbb7cb-filterLabel {
    font-weight: 600;
    color: var(--ink);
    font-size: 14px;
  }
  .s-ccbb7cb-filterSelect {
    min-height: 40px;
    padding: 8px 12px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    font: inherit;
    font-size: 14px;
    color: var(--ink);
    cursor: pointer;
    min-width: 160px;
  }
  .s-ccbb7cb-filterSelect:focus {
    outline: none;
    border-color: var(--link);
    box-shadow: var(--focus-ring);
  }

  /* 폼 섹션 - 강조 */
  .s-ccbb7cb-formSection {
    padding: 24px;
    border-color: var(--border-strong);
    box-shadow: var(--shadow-card);
  }
  .s-ccbb7cb-formHeader {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 8px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border);
  }
  .s-ccbb7cb-formTitle {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: var(--ink);
  }
  .s-ccbb7cb-formBadge {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    font-size: 12px;
    font-weight: 600;
    border-radius: 999px;
    background: var(--surface-sea);
    color: var(--primary);
  }

  .s-ccbb7cb-form {
    display: grid;
    gap: 16px;
  }
  .s-ccbb7cb-formRow {
    display: grid;
    gap: 12px;
  }
  .s-ccbb7cb-formRow.twoCols {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .s-ccbb7cb-form label {
    display: grid;
    gap: 6px;
    font-weight: 600;
    font-size: 14px;
    color: var(--ink);
  }
  .s-ccbb7cb-form input,
  .s-ccbb7cb-form textarea,
  .s-ccbb7cb-form select {
    width: 100%;
    min-height: 44px;
    padding: 10px 12px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 15px;
    color: var(--ink);
    background: var(--surface);
    transition: border-color 0.15s, box-shadow 0.15s;
  }
  .s-ccbb7cb-form input:hover,
  .s-ccbb7cb-form textarea:hover,
  .s-ccbb7cb-form select:hover {
    border-color: var(--link);
  }
  .s-ccbb7cb-form input:focus,
  .s-ccbb7cb-form textarea:focus,
  .s-ccbb7cb-form select:focus {
    outline: none;
    border-color: var(--link);
    box-shadow: var(--focus-ring);
  }
  .s-ccbb7cb-form textarea {
    min-height: 100px;
    resize: vertical;
  }
  .s-ccbb7cb-form input::placeholder,
  .s-ccbb7cb-form textarea::placeholder {
    color: var(--ink-muted);
  }

  .s-ccbb7cb-error {
    margin: 0;
    padding: 10px 12px;
    font-size: 13px;
    color: var(--danger);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius-sm);
  }

  .s-ccbb7cb-formActions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding-top: 8px;
    margin-top: 4px;
    border-top: 1px solid var(--border);
  }

  /* 버튼 */
  .s-ccbb7cb-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 44px;
    padding: 0 20px;
    font: inherit;
    font-size: 14px;
    font-weight: 600;
    border-radius: var(--radius-sm);
    cursor: pointer;
    border: 1px solid transparent;
    transition: background-color 0.15s, border-color 0.15s, color 0.15s;
  }
  .s-ccbb7cb-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .s-ccbb7cb-btn:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
  }

  .s-ccbb7cb-btnPrimary {
    background: var(--primary);
    color: var(--on-primary);
    border-color: var(--primary);
  }
  .s-ccbb7cb-btnPrimary:hover:not(:disabled) {
    background: var(--primary-hover);
    border-color: var(--primary-hover);
  }

  .s-ccbb7cb-btnSecondary {
    background: var(--surface);
    color: var(--ink);
    border-color: var(--border-strong);
  }
  .s-ccbb7cb-btnSecondary:hover:not(:disabled) {
    background: var(--surface-soft);
    border-color: var(--link);
  }

  .s-ccbb7cb-btnDanger {
    background: var(--danger);
    color: var(--on-primary);
    border-color: var(--danger);
  }
  .s-ccbb7cb-btnDanger:hover:not(:disabled) {
    background: #9d1f18;
    border-color: #9d1f18;
  }

  .s-ccbb7cb-btnGhost {
    background: transparent;
    color: var(--ink-muted);
    border-color: transparent;
    padding: 0 12px;
  }
  .s-ccbb7cb-btnGhost:hover:not(:disabled) {
    background: var(--surface-soft);
    color: var(--ink);
  }

  /* 목록 섹션 */
  .s-ccbb7cb-listSection {
    padding: 0;
    overflow: hidden;
  }

  .s-ccbb7cb-list {
    display: grid;
    gap: 0;
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .s-ccbb7cb-listItem {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--border);
    transition: background-color 0.15s;
  }
  .s-ccbb7cb-listItem:last-child {
    border-bottom: none;
  }
  .s-ccbb7cb-listItem:hover {
    background: var(--surface-soft);
  }

  .s-ccbb7cb-listContent {
    display: grid;
    gap: 4px;
    min-width: 0;
    flex: 1;
  }

  .s-ccbb7cb-listTitle {
    font-weight: 600;
    font-size: 15px;
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .s-ccbb7cb-listMeta {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 13px;
    color: var(--ink-muted);
    flex-wrap: wrap;
  }

  .s-ccbb7cb-statusBadge {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    font-size: 12px;
    font-weight: 600;
    border-radius: 999px;
  }
  .s-ccbb7cb-statusBadgeActive {
    background: var(--success-bg);
    color: var(--success-ink);
    border: 1px solid var(--success-border);
  }
  .s-ccbb7cb-statusBadgeCancelled {
    background: var(--danger-bg);
    color: var(--danger);
    border: 1px solid var(--danger-border);
  }
  .s-ccbb7cb-statusBadgeInactive {
    background: var(--warning-bg);
    color: var(--warning-ink);
    border: 1px solid var(--warning-border);
  }

  .s-ccbb7cb-listActions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }

  .s-ccbb7cb-listBtn {
    min-height: 36px;
    padding: 0 14px;
    font-size: 13px;
  }

  /* 빈 상태 */
  .s-ccbb7cb-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 48px 20px;
    text-align: center;
    color: var(--ink-muted);
  }
  .s-ccbb7cb-emptyIcon {
    font-size: 40px;
    margin-bottom: 12px;
    opacity: 0.5;
  }
  .s-ccbb7cb-emptyText {
    font-size: 15px;
    font-weight: 500;
    color: var(--ink);
    margin-bottom: 4px;
  }
  .s-ccbb7cb-emptySubtext {
    font-size: 13px;
  }

  /* 로딩/에러 */
  .s-ccbb7cb-status {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 32px 20px;
    font-size: 14px;
    color: var(--ink-muted);
  }
  .s-ccbb7cb-status.error {
    color: var(--danger);
    background: var(--danger-bg);
    border: 1px solid var(--danger-border);
    border-radius: var(--radius);
    margin: 16px 20px 0;
  }

  /* 페이징 */
  .s-ccbb7cb-paging {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 16px 20px;
    border-top: 1px solid var(--border);
  }
  .s-ccbb7cb-pagingBtn {
    min-height: 40px;
    min-width: 40px;
    padding: 0 12px;
    font: inherit;
    font-size: 14px;
    font-weight: 600;
    border-radius: var(--radius-sm);
    cursor: pointer;
    border: 1px solid var(--border-strong);
    background: var(--surface);
    color: var(--ink);
    transition: background-color 0.15s, border-color 0.15s, color 0.15s;
  }
  .s-ccbb7cb-pagingBtn:hover:not(:disabled) {
    background: var(--surface-soft);
    border-color: var(--link);
    color: var(--link);
  }
  .s-ccbb7cb-pagingBtn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
  .s-ccbb7cb-pagingInfo {
    font-size: 13px;
    color: var(--ink-muted);
    padding: 0 8px;
  }

  /* 반응형 */
  @media (max-width: 640px) {
    .s-ccbb7cb-formRow.twoCols {
      grid-template-columns: 1fr;
    }
    .s-ccbb7cb-formHeader {
      flex-direction: column;
      align-items: flex-start;
    }
    .s-ccbb7cb-listItem {
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }
    .s-ccbb7cb-listActions {
      width: 100%;
      justify-content: flex-end;
    }
    .s-ccbb7cb-filterSection {
      flex-direction: column;
      align-items: flex-start;
    }
    .s-ccbb7cb-filterSelect {
      width: 100%;
    }
  }
`

export default styles