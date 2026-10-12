import { useQueries } from '@tanstack/react-query'
import { getInvestorProfits, profitKeys, type SaleProfit } from '@/entities/profit/index.ts'

export interface SaleProfitRow extends SaleProfit {
  investorName: string
}

export interface ProfitPeriod {
  from: string
  to: string
}

// 둘 다 비우면 전체 기간, 둘 다 있으면 시작일이 종료일 이전일 때만 서버에 조회한다(입력 중인 한쪽만 빈 상태는 조회하지 않는다).
function toQueryablePeriod(period: ProfitPeriod | undefined): ProfitPeriod | undefined {
  if (period === undefined) return undefined
  if ((period.from === '') !== (period.to === '')) return undefined
  if (period.from > period.to) return undefined
  return period
}

/** 모든 투자자의 매도 수익을 하나의 목록(매도일 최신순)으로 모은다. */
export function useAllInvestorProfits(
  investors: { id: number; displayName: string }[],
  period: ProfitPeriod | undefined,
) {
  const queryPeriod = toQueryablePeriod(period)
  const results = useQueries({
    queries: investors.map((investor) => ({
      queryKey: profitKeys.investor(
        investor.id,
        queryPeriod?.from || undefined,
        queryPeriod?.to || undefined,
      ),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        getInvestorProfits(investor.id, queryPeriod, signal),
      enabled: queryPeriod !== undefined,
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
