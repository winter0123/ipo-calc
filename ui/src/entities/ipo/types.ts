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

// 공모주 상세 부가 정보(GET /ipos/{id}/insight). 출처에 없는 값은 null.
export const ipoInsightSchema = z.object({
  description: z.string().nullable(),
  keywords: z.array(z.string()),
  ceoName: z.string().nullable(),
  establishedOn: z.string().nullable(),
  address: z.string().nullable(),
  demandForecastStartDate: z.string().nullable(),
  demandForecastEndDate: z.string().nullable(),
  institutionalCompetitionRate: z.number().nullable(),
  lockUpCommitmentRatio: z.number().nullable(),
  protectedShareRatio: z.number().nullable(),
  floatShareRatio: z.number().nullable(),
  listedShares: z.number().nullable(),
  offeringShares: z.number().nullable(),
  marketCapAtOfferPrice: z.number().nullable(),
  recentSales: z.number().nullable(),
  recentNetProfit: z.number().nullable(),
  registrationStatementUrl: z.string().nullable(),
  brokers: z.array(
    z.object({
      name: z.string().nullable(),
      shares: z.number().nullable(),
      equalSharesPerPerson: z.number().nullable(),
      proportionalCompetitionRate: z.number().nullable(),
    }),
  ),
})

export type IpoInsight = z.infer<typeof ipoInsightSchema>
