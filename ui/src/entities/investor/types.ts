import { z } from 'zod'

export interface InvestorRegisterRequest {
  displayName: string
  relationship?: InvestorRelationship
}

export type InvestorRelationship = 'SELF' | 'SPOUSE' | 'PARENT' | 'CHILD' | 'OTHER'

export const investorResponseSchema = z.object({
  id: z.number(),
  displayName: z.string(),
  accountId: z.number(),
  status: z.string(),
  deletedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type InvestorResponse = z.infer<typeof investorResponseSchema>
