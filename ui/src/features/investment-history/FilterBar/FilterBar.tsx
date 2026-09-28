import styles from './FilterBar.module.css'

export interface InvestmentHistoryFilters {
  investorId: string
  securitiesAccountId: string
  ipoId: string
}

export interface FilterBarProps {
  filters: InvestmentHistoryFilters
  onFiltersChange: (field: keyof InvestmentHistoryFilters, value: string) => void
  onReset: () => void
}

export default function FilterBar({ filters, onFiltersChange, onReset }: FilterBarProps) {
  return (
    <div className={styles.container} role="search" aria-label="투자 내역 필터">
      <div className={styles.fieldGroup}>
        <label htmlFor="investorId" className={styles.label}>
          투자자 ID
        </label>
        <input
          id="investorId"
          type="text"
          className={styles.input}
          value={filters.investorId}
          onChange={(e) => onFiltersChange('investorId', e.target.value)}
          placeholder="투자자 ID 입력"
          aria-describedby="investorId-hint"
        />
        <span id="investorId-hint" className={styles.hint}>
          비워두면 전체 조회
        </span>
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="securitiesAccountId" className={styles.label}>
          증권계좌 ID
        </label>
        <input
          id="securitiesAccountId"
          type="text"
          className={styles.input}
          value={filters.securitiesAccountId}
          onChange={(e) => onFiltersChange('securitiesAccountId', e.target.value)}
          placeholder="증권계좌 ID 입력"
          aria-describedby="securitiesAccountId-hint"
        />
        <span id="securitiesAccountId-hint" className={styles.hint}>
          비워두면 전체 조회
        </span>
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="ipoId" className={styles.label}>
          IPO ID
        </label>
        <input
          id="ipoId"
          type="text"
          className={styles.input}
          value={filters.ipoId}
          onChange={(e) => onFiltersChange('ipoId', e.target.value)}
          placeholder="IPO ID 입력"
          aria-describedby="ipoId-hint"
        />
        <span id="ipoId-hint" className={styles.hint}>
          비워두면 전체 조회
        </span>
      </div>

      <button
        type="button"
        className={styles.resetButton}
        onClick={onReset}
        aria-label="필터 초기화"
      >
        초기화
      </button>
    </div>
  )
}
