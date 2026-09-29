import { useQuery } from '@tanstack/react-query'
import { getSaleProfit } from '../../entities/profit/api-client.ts'
import type { SaleProfit } from '../../entities/profit/types.ts'

interface UseSaleProfitOptions {
  saleId: number | undefined
  enabled: boolean
}

export function useSaleProfit({ saleId, enabled }: UseSaleProfitOptions) {
  return useQuery<SaleProfit, Error>({
    queryKey: ['saleProfit', saleId],
    queryFn: () => getSaleProfit(saleId!),
    enabled: enabled && saleId !== undefined && saleId !== null,
  })
}
