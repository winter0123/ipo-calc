import styles from './FilterBar.module.css'
import AccountSelector from '../AccountSelector/AccountSelector.tsx'

export interface InvestmentHistoryFilters {
  investorId: string
  securitiesAccountId: string
  ipoId: string
}

export interface FilterBarProps {
  filters: InvestmentHistoryFilters
  onFiltersChange: (patch: Partial<InvestmentHistoryFilters>) => void
  onReset: () => void
}

export default function FilterBar({ filters, onFiltersChange, onReset }: FilterBarProps) {
  const handleSecuritiesAccountChange = (value: string) => {
    onFiltersChange({ securitiesAccountId: value })
  }

  return (
    <div className={styles.container} role="search" aria-label="투자 내역 필터">
      <AccountSelector
        investorId={filters.investorId}
        securitiesAccountId={filters.securitiesAccountId}
        onSecuritiesAccountChange={handleSecuritiesAccountChange}
      />

      <div className={styles.fieldGroup}>
        <label htmlFor="ipoId" className={styles.label}>
          IPO ID
        </label>
        <input
          id="ipoId"
          type="text"
          className={styles.input}
          value={filters.ipoId}
          onChange={(e) => onFiltersChange({ ipoId: e.target.value })}
          placeholder="IPO ID 입력"
          aria-describedby="ipoId-hint"
          style={{ minWidth: 44 }}
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
