import { z } from 'zod'

export interface SaleRegisterRequest {
  allocationId?: number
  quantity: number
  price?: number
  amount?: number
  saleDate: string
  fee?: number
  tax?: number
  otherCost?: number
  /** 직접 매도 등록 시 필요한 필드 */
  allocatedQuantity?: number
  securitiesAccountId?: number
  ipoId?: number
  investorId?: number
}

export const saleSchema = z.object({
  id: z.number(),
  allocationId: z.number(),
  quantity: z.number(),
  price: z.number().nullable(),
  amount: z.number().nullable(),
  saleDate: z.string(),
  fee: z.number().nullable(),
  tax: z.number().nullable(),
  otherCost: z.number().nullable(),
  status: z.string(),
  deletedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type Sale = z.infer<typeof saleSchema>

export type SaleUpdateRequest = Partial<Omit<SaleRegisterRequest, 'allocationId'>>
