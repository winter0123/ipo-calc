import { useQuery } from '@tanstack/react-query'
import type { DashboardTab } from './useDashboardTabs.ts'

interface SummaryData {
  totalInvestmentAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
  totalProfitRate: number
  participationCount: number
}

interface InvestorAggregation {
  id: number
  investorName: string
  totalAmount: number
}

interface SecuritiesFirmAggregation {
  id: number
  securitiesFirmName: string
  totalAmount: number
}

interface PeriodAggregation {
  period: string
  totalAmount: number
}

type DashboardResponse =
  SummaryData | InvestorAggregation[] | SecuritiesFirmAggregation[] | PeriodAggregation[]

function getMockData(tab: DashboardTab): DashboardResponse {
  switch (tab) {
    case 'all':
      return {
        totalInvestmentAmount: 100000000,
        totalPreTaxProfit: 15000000,
        totalAfterTaxProfit: 12000000,
        totalProfitRate: 12.5,
        participationCount: 45,
      }
    case 'investor':
      return [
        { id: 1, investorName: '김철수', totalAmount: 50000000 },
        { id: 2, investorName: '박영희', totalAmount: 30000000 },
      ]
    case 'securities-firm':
      return [
        { id: 1, securitiesFirmName: '키움증권', totalAmount: 40000000 },
        { id: 2, securitiesFirmName: '미래에셋증권', totalAmount: 35000000 },
      ]
    case 'month':
      return [
        { period: '2026-09', totalAmount: 15000000 },
        { period: '2026-08', totalAmount: 12000000 },
      ]
    case 'year':
      return [
        { period: '2026', totalAmount: 120000000 },
        { period: '2025', totalAmount: 95000000 },
      ]
  }
}

async function mockFetchDashboardData(tab: DashboardTab): Promise<DashboardResponse> {
  await new Promise((resolve) => setTimeout(resolve, 300))
  return getMockData(tab)
}

export function useDashboardSummary() {
  return useQuery({
    queryKey: ['dashboardSummary'],
    queryFn: () => mockFetchDashboardData('all'),
    enabled: true,
    staleTime: 0,
    select: (data) => data,
  })
}

export function useDashboardByInvestor() {
  return useQuery({
    queryKey: ['dashboardByInvestor'],
    queryFn: () => mockFetchDashboardData('investor'),
    enabled: true,
    staleTime: 0,
    select: (data) => data,
  })
}

export function useDashboardBySecuritiesFirm() {
  return useQuery({
    queryKey: ['dashboardBySecuritiesFirm'],
    queryFn: () => mockFetchDashboardData('securities-firm'),
    enabled: true,
    staleTime: 0,
    select: (data) => data,
  })
}

export function useDashboardByPeriod(unit: 'month' | 'year') {
  return useQuery({
    queryKey: ['dashboardByPeriod', { unit }],
    queryFn: () => mockFetchDashboardData(unit),
    enabled: true,
    staleTime: 0,
    select: (data) => data,
  })
}
