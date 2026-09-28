import { useState, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { InvestmentHistoryFilters } from './FilterBar'

export function useInvestmentHistoryFilters() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<InvestmentHistoryFilters>({
    investorId: '',
    securitiesAccountId: '',
    ipoId: '',
  })

  const handleChange = useCallback((field: keyof InvestmentHistoryFilters, value: string) => {
    setFilters((prev) => ({ ...prev, [field]: value }))
  }, [])

  const handleReset = useCallback(() => {
    const emptyFilters: InvestmentHistoryFilters = {
      investorId: '',
      securitiesAccountId: '',
      ipoId: '',
    }
    setFilters(emptyFilters)
    queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
  }, [queryClient])

  return {
    filters,
    handleChange,
    handleReset,
  }
}
