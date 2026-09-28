export interface DashboardSummary {
  totalInvestmentAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
  totalProfitRate: number
  participationCount: number
}

export interface DashboardByInvestor {
  investorId: number
  investorName: string
  totalInvestmentAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
  totalProfitRate: number
  participationCount: number
}

export interface DashboardBySecuritiesFirm {
  securitiesFirmId: number
  securitiesFirmName: string
  totalInvestmentAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
  totalProfitRate: number
  participationCount: number
}

export interface DashboardByPeriod {
  unit: 'month' | 'year'
  year?: number
  month?: number
  data: Array<{
    period: string
    totalInvestmentAmount: number
    totalPreTaxProfit: number
    totalAfterTaxProfit: number
    totalProfitRate: number
    participationCount: number
  }>
}
