import { z } from 'zod'

// DB(V5)에서 nullable인 컬럼은 .nullable()로 둔다.
export const ipoSchema = z.object({
  id: z.number(),
  companyName: z.string(),
  ipoName: z.string(),
  stockCode: z.string().nullable(),
  roundLabel: z.string().nullable(),
  lowerBandPrice: z.number().nullable(),
  upperBandPrice: z.number().nullable(),
  fixedPrice: z.number().nullable(),
  subscriptionStartDate: z.string(),
  subscriptionEndDate: z.string(),
  listingDate: z.string().nullable(),
  refundDate: z.string().nullish(),
  refundDateEstimated: z.boolean().nullish(),
  offeringShareCount: z.number().nullable(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type Ipo = z.infer<typeof ipoSchema>

export interface IpoRegisterRequest {
  companyName: string
  ipoName: string
  stockCode?: string
  roundLabel?: string
  lowerBandPrice?: number
  upperBandPrice?: number
  fixedPrice?: number
  subscriptionStartDate: string
  subscriptionEndDate: string
  listingDate?: string
  offeringShareCount?: number
}
