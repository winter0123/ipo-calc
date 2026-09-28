import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import FilterBar, {
  type InvestmentHistoryFilters,
} from '../../features/investment-history/FilterBar/FilterBar.tsx'
import { getInvestmentHistory } from '../../entities/subscription/api-client.ts'
import type { InvestmentHistoryResponse } from '../../entities/subscription/types.ts'
import EmptyState from '../../widgets/EmptyState/EmptyState.tsx'
import styles from './InvestmentHistoryPage.module.css'

function filtersToApiParams(filters: InvestmentHistoryFilters): Record<string, number> {
  const params: Record<string, number> = {}
  if (filters.investorId !== '') params.investorId = Number(filters.investorId)
  if (filters.securitiesAccountId !== '')
    params.securitiesAccountId = Number(filters.securitiesAccountId)
  if (filters.ipoId !== '') params.ipoId = Number(filters.ipoId)
  return params
}

export default function InvestmentHistoryPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<InvestmentHistoryFilters>({
    investorId: '',
    securitiesAccountId: '',
    ipoId: '',
  })
  const apiFilters = filtersToApiParams(filters)

  // Ref to always have current filters for queryFn (avoids stale closure)
  const apiFiltersRef = useRef(apiFilters)

  // Update ref after render (avoids "Cannot update ref during render" lint error)
  useEffect(() => {
    apiFiltersRef.current = apiFilters
  }, [apiFilters])

  // Invalidate queries after filters change (runs after render, so ref is current)
  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
  }, [filters, queryClient])

  const handleChange = (field: keyof InvestmentHistoryFilters, value: string) => {
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

  const { data, isLoading, error } = useQuery<InvestmentHistoryResponse>({
    queryKey: ['investmentHistory'],
    queryFn: () => getInvestmentHistory(apiFiltersRef.current),
  })

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

        {!isLoading && !error && items.length > 0 && (
          <div className={styles.placeholder}>
            투자 내역 {items.length}건 조회됨 (테이블은 다음 SF에서 구현)
          </div>
        )}
      </div>
    </div>
  )
}
