import { z } from 'zod'

// 응답 스키마가 단일 출처다. 타입은 스키마에서 파생하고, api-client가 런타임에 파싱한다.
export const dashboardSummarySchema = z.object({
  totalInvestmentAmount: z.number(),
  totalPreTaxProfit: z.number(),
  totalAfterTaxProfit: z.number(),
  totalProfitRate: z.number(),
  participationCount: z.number(),
})

export const dashboardByInvestorSchema = dashboardSummarySchema.extend({
  investorId: z.number(),
  investorName: z.string(),
})

export const dashboardBySecuritiesFirmSchema = dashboardSummarySchema.extend({
  securitiesFirmId: z.number(),
  securitiesFirmName: z.string(),
})

export const dashboardByPeriodSchema = dashboardSummarySchema.extend({
  period: z.string(),
})

export type DashboardSummary = z.infer<typeof dashboardSummarySchema>
export type DashboardByInvestor = z.infer<typeof dashboardByInvestorSchema>
export type DashboardBySecuritiesFirm = z.infer<typeof dashboardBySecuritiesFirmSchema>
export type DashboardByPeriod = z.infer<typeof dashboardByPeriodSchema>

export type PeriodUnit = 'month' | 'year'
