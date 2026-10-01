import { apiGetParsed } from '@/shared/api/parsed.ts'
import { z } from 'zod'
import {
  dashboardSummarySchema,
  dashboardByInvestorSchema,
  dashboardBySecuritiesFirmSchema,
  dashboardByPeriodSchema,
} from './types.ts'
import type {
  DashboardSummary,
  DashboardByInvestor,
  DashboardBySecuritiesFirm,
  DashboardByPeriod,
  PeriodUnit,
} from './types.ts'

export async function getDashboardSummary(signal?: AbortSignal): Promise<DashboardSummary> {
  return apiGetParsed('/dashboard/summary', dashboardSummarySchema, signal)
}

export async function getDashboardByInvestor(signal?: AbortSignal): Promise<DashboardByInvestor[]> {
  return apiGetParsed('/dashboard/by-investor', z.array(dashboardByInvestorSchema), signal)
}

export async function getDashboardBySecuritiesFirm(
  signal?: AbortSignal,
): Promise<DashboardBySecuritiesFirm[]> {
  return apiGetParsed(
    '/dashboard/by-securities-firm',
    z.array(dashboardBySecuritiesFirmSchema),
    signal,
  )
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
  return apiGetParsed(
    `/dashboard/by-period?${params.toString()}`,
    z.array(dashboardByPeriodSchema),
    signal,
  )
}
