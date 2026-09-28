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

export async function getDashboardByPeriod(unit: 'month' | 'year'): Promise<DashboardByPeriod[]> {
  return apiFetch<DashboardByPeriod[]>(`/dashboard/by-period?unit=${unit}`, { method: 'GET' })
}
