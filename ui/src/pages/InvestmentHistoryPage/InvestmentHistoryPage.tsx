import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  FilterBar,
  type InvestmentHistoryFilters as FilterInvestmentHistoryFilters,
} from '@/features/investment-history/index.ts'
import {
  useInvestmentHistory,
  investmentHistoryKeys,
  type InvestmentHistoryFilters,
} from '@/entities/subscription/index.ts'
import { useInvestors } from '@/entities/investor/index.ts'
import { EmptyState } from '@/widgets/EmptyState/index.ts'
import { InvestmentHistoryTable } from '@/widgets/InvestmentHistoryTable/index.ts'
import styles from './InvestmentHistoryPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

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

  const { data: investors, isLoading: investorsLoading, error: investorsError } = useInvestors()
  const { data, isLoading, error } = useInvestmentHistory(apiFilters)

  const getErrorMessage = () =>
    toErrorMessage(error, {
      byStatus: { 404: '투자 내역을 찾을 수 없습니다.' },
      fallback: '투자 내역을 불러오는 데 실패했습니다.',
    })

  const handleChange = (field: keyof FilterInvestmentHistoryFilters, value: string) => {
    setFilters((prev) => ({ ...prev, [field]: value }))
  }

  const handleReset = () => {
    setFilters({
      investorId: '',
      securitiesAccountId: '',
      ipoId: '',
    })
    queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
  }

  const items = data?.content ?? []
  const hasInvestors = investors && investors.length > 0

  if (investorsLoading) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>투자 내역</h1>
        <p className={styles.loading}>투자자 목록을 불러오는 중...</p>
      </div>
    )
  }

  if (investorsError) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>투자 내역</h1>
        <p className={styles.error} role="alert">
          투자자 목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
        </p>
      </div>
    )
  }

  if (!hasInvestors) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>투자 내역</h1>
        <div className={styles.emptyInvestors} role="status" aria-live="polite">
          <p className={styles.emptyMessage}>
            등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요.
          </p>
          <Link to="/investors/register" className={styles.registerLink}>
            투자자 등록하기
          </Link>
        </div>
      </div>
    )
  }

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
