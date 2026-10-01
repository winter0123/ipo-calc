import { z } from 'zod'

export const brokerCredentialSchema = z.object({
  id: z.number().nullable(),
  securitiesAccountId: z.number(),
  apiProvider: z.string().nullable(),
  connectionStatus: z.enum(['CONNECTED', 'EXPIRED', 'ERROR', 'DISCONNECTED']),
  accountNumber: z.string().nullable(),
  lastSyncedAt: z.string().nullable(),
  supportedProvider: z.string().nullable(),
  keyIssuedOn: z.string().nullable(),
  keyExpiresOn: z.string().nullable(),
})

export type BrokerCredential = z.infer<typeof brokerCredentialSchema>

export const unmatchedReasonSchema = z.enum([
  'NO_ALLOCATION_FOR_STOCK',
  'AMBIGUOUS_ALLOCATIONS',
  'ALREADY_REGISTERED',
  'SPLIT_SALE_MISMATCH',
  'QUANTITY_EXCEEDS_ALLOCATION',
])

export type UnmatchedReason = z.infer<typeof unmatchedReasonSchema>

export const syncPreviewItemSchema = z.object({
  tradeDate: z.string(),
  stockCode: z.string(),
  quantity: z.number(),
  amount: z.number(),
  price: z.number(),
  fee: z.number(),
  tax: z.number(),
  allocationId: z.number().nullable(),
  reason: unmatchedReasonSchema.nullable(),
})

export type SyncPreviewItem = z.infer<typeof syncPreviewItemSchema>

export const syncPreviewSchema = z.object({
  matched: z.array(syncPreviewItemSchema),
  unmatched: z.array(syncPreviewItemSchema),
  allocationsWithoutStockCode: z.array(z.number()),
})

export type SyncPreview = z.infer<typeof syncPreviewSchema>

export interface BrokerCredentialRegisterRequest {
  apiProvider: string
  appKey: string
  appSecret: string
  keyIssuedOn?: string
}

export interface BrokerCredentialReplaceRequest {
  appKey: string
  appSecret: string
  keyIssuedOn?: string
}

export interface SyncRange {
  from: string
  to: string
}

export interface SyncConfirmSale {
  allocationId: number
  quantity: number
  price: number
  amount: number
  saleDate: string
  fee: number
  tax: number
}
