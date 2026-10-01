import { useQuery } from '@tanstack/react-query'
import { getSaleProfit, profitKeys, type SaleProfit } from '../../entities/profit/index.ts'

interface UseSaleProfitOptions {
  saleId: number | undefined
  enabled: boolean
}

export function useSaleProfit({ saleId, enabled }: UseSaleProfitOptions) {
  return useQuery<SaleProfit, Error>({
    queryKey: profitKeys.sale(saleId),
    queryFn: ({ signal }) => getSaleProfit(saleId!, signal),
    enabled: enabled && saleId !== undefined && saleId !== null,
  })
}
