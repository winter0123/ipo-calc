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
  companyName?: string
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

// 공모주 상세 공시 정보(GET /ipos/{id}/insight). DART 공시에서 DB에 저장한 값, 없는 값은 null.
export const ipoInsightSchema = z.object({
  syncedAt: z.string().nullable(),
  sourceReportName: z.string().nullable(),
  sourceUrl: z.string().nullable(),
  ceoName: z.string().nullable(),
  establishedOn: z.string().nullable(),
  address: z.string().nullable(),
  parValue: z.number().nullable(),
  capitalStock: z.number().nullable(),
  largestShareholderName: z.string().nullable(),
  largestShareholderRatio: z.number().nullable(),
  institutionalCompetitionRate: z.number().nullable(),
  lockUpCommitmentRatio: z.number().nullable(),
  financials: z.array(
    z.object({
      period: z.string(),
      revenue: z.number().nullable(),
      operatingProfit: z.number().nullable(),
      netIncome: z.number().nullable(),
    }),
  ),
  floatSchedule: z.array(
    z.object({
      label: z.string(),
      shares: z.number().nullable(),
      ratio: z.number().nullable(),
    }),
  ),
  underwriters: z.array(
    z.object({
      name: z.string(),
      role: z.string().nullable(),
      shares: z.number().nullable(),
    }),
  ),
})

export type IpoInsight = z.infer<typeof ipoInsightSchema>

// 관리자 "지금 가져오기"(POST /ipos/sync) 결과 요약.
export const ipoSyncResultSchema = z.object({
  created: z.number(),
  updated: z.number(),
  skipped: z.number(),
  failed: z.number(),
  scheduleMerged: z.number(),
  detailSaved: z.number(),
  aborted: z.boolean(),
})

export type IpoSyncResult = z.infer<typeof ipoSyncResultSchema>

// 동기화 상태 객체 (POST /ipos/sync 및 GET /ipos/sync/status)
export const ipoSyncStatusSchema = z.object({
  state: z.enum(['IDLE', 'RUNNING', 'DONE', 'FAILED']),
  fromMonth: z
    .string()
    .regex(/^\d{4}-\d{2}$/)
    .nullable(),
  toMonth: z
    .string()
    .regex(/^\d{4}-\d{2}$/)
    .nullable(),
  monthsDone: z.number().int().nonnegative().nullable(),
  monthsTotal: z.number().int().nonnegative().nullable(),
  startedAt: z
    .string()
    .nullable()
    .refine((val) => val === null || !isNaN(Date.parse(val)), { message: 'Invalid datetime' }),
  finishedAt: z
    .string()
    .nullable()
    .refine((val) => val === null || !isNaN(Date.parse(val)), { message: 'Invalid datetime' }),
  result: ipoSyncResultSchema.nullable(),
  error: z.string().nullable(),
})

export type IpoSyncStatus = z.infer<typeof ipoSyncStatusSchema>
