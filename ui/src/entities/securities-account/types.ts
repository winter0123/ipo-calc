import { z } from 'zod'

export const securitiesAccountSchema = z.object({
  id: z.number(),
  investorId: z.number(),
  securitiesFirmId: z.number(),
  securitiesFirmName: z.string().nullable(),
  accountNumber: z.string(),
  accountName: z.string().nullable(),
  isDefault: z.boolean(),
  status: z.string(),
  deletedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type SecuritiesAccount = z.infer<typeof securitiesAccountSchema>

export const revealedSecuritiesAccountNumberSchema = z.object({
  id: z.number(),
  accountNumber: z.string(),
  exposedAt: z.string(),
})

export type RevealedSecuritiesAccountNumber = z.infer<typeof revealedSecuritiesAccountNumberSchema>

export interface SecuritiesAccountRegisterRequest {
  investorId: number
  securitiesFirmId: number
  accountNumber: string
  accountName?: string
  isDefault?: boolean
}
