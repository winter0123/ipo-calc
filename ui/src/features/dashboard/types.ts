export interface DashboardSummary {
  totalInvestmentAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
  totalProfitRate: number
  participationCount: number
}

export interface DashboardByInvestor extends DashboardSummary {
  investorId: number
  investorName: string
}

export interface DashboardBySecuritiesFirm extends DashboardSummary {
  securitiesFirmId: number
  securitiesFirmName: string
}

export interface DashboardByPeriod extends DashboardSummary {
  period: string
}

export type PeriodUnit = 'month' | 'year'
