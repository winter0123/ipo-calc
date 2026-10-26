import { useIpos } from '@/entities/ipo/index.ts'
import styles, { StyleScope } from './FilterBar.styles.ts'
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
  const { data: ipos } = useIpos()

  const handleSecuritiesAccountChange = (value: string) => {
    onFiltersChange({ securitiesAccountId: value })
  }

  return (
    <StyleScope>
      {
        <div className={styles.container} role="search" aria-label="투자 내역 필터">
          <AccountSelector
            investorId={filters.investorId}
            securitiesAccountId={filters.securitiesAccountId}
            onSecuritiesAccountChange={handleSecuritiesAccountChange}
          />

          <div className={styles.fieldGroup}>
            <label htmlFor="ipoId" className={styles.label}>
              공모주
            </label>
            <select
              id="ipoId"
              className={styles.input}
              value={filters.ipoId}
              onChange={(e) => onFiltersChange({ ipoId: e.target.value })}
            >
              <option value="">전체</option>
              {ipos?.map((ipo) => (
                <option key={ipo.id} value={String(ipo.id)}>
                  {ipo.ipoName}
                </option>
              ))}
            </select>
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
      }
    </StyleScope>
  )
}
