import { useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import {
  FilterBar,
  type InvestmentHistoryFilters as FilterInvestmentHistoryFilters,
} from '@/features/investment-history/index.ts'
import {
  useInvestmentHistory,
  investmentHistoryKeys,
  type InvestmentHistoryFilters,
} from '@/entities/subscription/index.ts'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
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

const FILTER_KEYS = ['securitiesAccountId', 'ipoId'] as const

// 필터는 URL 쿼리 문자열이 단일 출처다(새로고침·링크 공유 시 유지). 변경은 history를 쌓지 않는다.
export default function InvestmentHistoryPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  // 투자자는 상단 전환기의 현재 투자자로 고정한다(URL로 바꾸지 않는다).
  const { investors, current, isLoading: investorsLoading } = useCurrentInvestor()
  const filters: FilterInvestmentHistoryFilters = {
    investorId: current ? String(current.id) : '',
    securitiesAccountId: searchParams.get('securitiesAccountId') ?? '',
    ipoId: searchParams.get('ipoId') ?? '',
  }
  const apiFilters = filtersToApiParams(filters)

  const { data, isLoading, error } = useInvestmentHistory(apiFilters, { enabled: !!current })

  const getErrorMessage = () =>
    toErrorMessage(error, {
      byStatus: { 404: '투자 내역을 찾을 수 없습니다.' },
      fallback: '투자 내역을 불러오는 데 실패했습니다.',
    })

  const handleChange = (patch: Partial<FilterInvestmentHistoryFilters>) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const key of FILTER_KEYS) {
          const value = patch[key]
          if (value === undefined) continue
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true },
    )
  }

  const handleReset = () => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const key of FILTER_KEYS) next.delete(key)
        return next
      },
      { replace: true },
    )
    queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
  }

  const items = data?.content ?? []
  const hasInvestors = investors.length > 0

  if (investorsLoading) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>투자 내역</h1>
        <p className={styles.loading}>투자자 목록을 불러오는 중...</p>
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
