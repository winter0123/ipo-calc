import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import FilterBar, {
  type InvestmentHistoryFilters as FilterInvestmentHistoryFilters,
} from '../../features/investment-history/FilterBar/FilterBar.tsx'
import { useInvestmentHistory } from '../../features/investment-history/useInvestmentHistory.ts'
import type { InvestmentHistoryFilters } from '../../entities/subscription/types.ts'
import EmptyState from '../../widgets/EmptyState/EmptyState.tsx'
import InvestmentHistoryTable from '../../widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx'
import styles from './InvestmentHistoryPage.module.css'

function filtersToApiParams(filters: FilterInvestmentHistoryFilters): InvestmentHistoryFilters {
  return {
    investorId: filters.investorId ? Number(filters.investorId) : undefined,
    securitiesAccountId: filters.securitiesAccountId
      ? Number(filters.securitiesAccountId)
      : undefined,
    ipoId: filters.ipoId ? Number(filters.ipoId) : undefined,
  }
}

export default function InvestmentHistoryPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<FilterInvestmentHistoryFilters>({
    investorId: '',
    securitiesAccountId: '',
    ipoId: '',
  })
  const apiFilters = filtersToApiParams(filters)

  const { data, isLoading, error } = useInvestmentHistory(apiFilters)

  const getErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
      return '투자 내역을 찾을 수 없습니다.'
    }
    if (
      msg.includes('Failed to fetch') ||
      msg.includes('Network error') ||
      msg.includes('Network request failed')
    ) {
      return '네트워크 연결을 확인해 주세요.'
    }
    return '투자 내역을 불러오는 데 실패했습니다.'
  }

  const handleChange = (field: keyof FilterInvestmentHistoryFilters, value: string) => {
    setFilters((prev) => ({ ...prev, [field]: value }))
  }

  const handleReset = () => {
    setFilters({
      investorId: '',
      securitiesAccountId: '',
      ipoId: '',
    })
    queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
  }

  const items = data?.content ?? []

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>투자 내역</h1>

      <FilterBar filters={filters} onFiltersChange={handleChange} onReset={handleReset} />

      <div className={styles.resultsArea}>
        {isLoading && <p className={styles.loading}>투자 내역을 불러오는 중...</p>}

        {error && (
          <p className={styles.error} role="alert">
            {getErrorMessage()}
          </p>
        )}

        {!isLoading && !error && items.length === 0 && <EmptyState onResetFilters={handleReset} />}

        {!isLoading && !error && items.length > 0 && <InvestmentHistoryTable items={items} />}
      </div>
    </div>
  )
}
