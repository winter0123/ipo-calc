interface EmptyStateProps {
  onResetFilters: () => void
}

export default function EmptyState({ onResetFilters }: EmptyStateProps) {
  return (
    <div className="empty-state-container" role="status" aria-live="polite">
      <div className="empty-state-content">
        <p className="empty-state-message">해당 조건에 해당하는 기록이 없습니다.</p>
        <button type="button" className="empty-state-reset-button" onClick={onResetFilters}>
          전체 필터 초기화
        </button>
      </div>
    </div>
  )
}
