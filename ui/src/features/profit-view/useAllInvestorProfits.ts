import { useQueries } from '@tanstack/react-query'
import { getInvestorProfits, profitKeys, type SaleProfit } from '@/entities/profit/index.ts'

export interface SaleProfitRow extends SaleProfit {
  investorName: string
}

/** 모든 투자자의 매도 수익을 하나의 목록(매도일 최신순)으로 모은다. */
export function useAllInvestorProfits(investors: { id: number; displayName: string }[]) {
  const results = useQueries({
    queries: investors.map((investor) => ({
      queryKey: profitKeys.investor(investor.id),
      queryFn: ({ signal }: { signal: AbortSignal }) => getInvestorProfits(investor.id, signal),
    })),
  })
  const rows: SaleProfitRow[] = results
    .flatMap((r, i) =>
      (r.data?.sales ?? []).map((sale) => ({ ...sale, investorName: investors[i].displayName })),
    )
    .sort((a, b) => b.saleDate.localeCompare(a.saleDate))
  return {
    rows,
    isLoading: results.some((r) => r.isLoading),
    error: results.find((r) => r.error)?.error ?? null,
  }
}
