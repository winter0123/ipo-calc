import styles from './EmptyState.module.css'

interface EmptyStateProps {
  onResetFilters: () => void
}

export default function EmptyState({ onResetFilters }: EmptyStateProps) {
  return (
    <div className={styles.container} role="status" aria-live="polite">
      <div className={styles.content}>
        <p className={styles.message}>해당 조건에 해당하는 기록이 없습니다.</p>
        <button type="button" className={styles.resetButton} onClick={onResetFilters}>
          전체 필터 초기화
        </button>
      </div>
    </div>
  )
}
