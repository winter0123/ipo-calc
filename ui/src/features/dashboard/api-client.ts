import { apiFetch } from '../../shared/api/client.ts'
import type {
  DashboardSummary,
  DashboardByInvestor,
  DashboardBySecuritiesFirm,
  DashboardByPeriod,
  PeriodUnit,
} from './types.ts'

export async function getDashboardSummary(signal?: AbortSignal): Promise<DashboardSummary> {
  return apiFetch<DashboardSummary>('/dashboard/summary', { method: 'GET', signal })
}

export async function getDashboardByInvestor(signal?: AbortSignal): Promise<DashboardByInvestor[]> {
  return apiFetch<DashboardByInvestor[]>('/dashboard/by-investor', { method: 'GET', signal })
}

export async function getDashboardBySecuritiesFirm(
  signal?: AbortSignal,
): Promise<DashboardBySecuritiesFirm[]> {
  return apiFetch<DashboardBySecuritiesFirm[]>('/dashboard/by-securities-firm', {
    method: 'GET',
    signal,
  })
}

export async function getDashboardByPeriod(
  unit: PeriodUnit,
  year?: number,
  month?: number,
  signal?: AbortSignal,
): Promise<DashboardByPeriod[]> {
  const params = new URLSearchParams({ unit })
  if (year !== undefined) params.set('year', String(year))
  if (month !== undefined) params.set('month', String(month))
  return apiFetch<DashboardByPeriod[]>(`/dashboard/by-period?${params.toString()}`, {
    method: 'GET',
    signal,
  })
}
