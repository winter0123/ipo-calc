import { z } from 'zod'
import { apiFetchParsed } from '@/shared/api/parsed.ts'
import { saleSchema } from '@/entities/sale/index.ts'
import type { SyncConfirmSale } from '@/entities/broker/index.ts'

export function confirmBrokerSync(
  securitiesAccountId: number,
  sales: SyncConfirmSale[],
): Promise<void> {
  return apiFetchParsed(
    `/securities-accounts/${securitiesAccountId}/broker-sync/confirm`,
    z.array(saleSchema),
    { method: 'POST', body: JSON.stringify({ sales }) },
  ).then(() => undefined)
}
