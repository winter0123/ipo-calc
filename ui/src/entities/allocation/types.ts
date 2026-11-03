import { z } from 'zod'

export interface AllocationRegisterRequest {
  subscriptionId?: number
  ipoId?: number
  investorId?: number
  securitiesAccountId?: number
  allocatedQuantity: number
  allocationMethod?: string
  note?: string
}

export const allocationSchema = z.object({
  id: z.number(),
  subscriptionId: z.number(),
  allocatedQuantity: z.number(),
  allocatedAt: z.string(),
  allocationMethod: z.string().nullable(),
  allocatedAmount: z.number().nullable(),
  note: z.string().nullable(),
  status: z.string(),
  deletedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type Allocation = z.infer<typeof allocationSchema>

export interface AllocationUpdateRequest {
  allocatedQuantity?: number
  allocationMethod?: string
  note?: string
}
