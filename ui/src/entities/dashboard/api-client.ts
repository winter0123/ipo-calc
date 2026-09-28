import { apiFetch } from '../../shared/api/client.ts'
import type {
  DashboardSummary,
  DashboardByInvestor,
  DashboardBySecuritiesFirm,
  DashboardByPeriod,
} from './types.ts'

export async function getDashboardSummary(): Promise<DashboardSummary> {
  return apiFetch<DashboardSummary>('/dashboard/summary', { method: 'GET' })
}

export async function getDashboardByInvestor(): Promise<DashboardByInvestor[]> {
  return apiFetch<DashboardByInvestor[]>('/dashboard/by-investor', { method: 'GET' })
}

export async function getDashboardBySecuritiesFirm(): Promise<DashboardBySecuritiesFirm[]> {
  return apiFetch<DashboardBySecuritiesFirm[]>('/dashboard/by-securities-firm', { method: 'GET' })
}

export async function getDashboardByPeriod(
  unit: 'month' | 'year',
  year?: number,
  month?: number,
): Promise<DashboardByPeriod[]> {
  const params = new URLSearchParams({ unit })
  if (year !== undefined) params.set('year', String(year))
  if (month !== undefined) params.set('month', String(month))
  return apiFetch<DashboardByPeriod[]>(`/dashboard/by-period?${params.toString()}`, {
    method: 'GET',
  })
}
