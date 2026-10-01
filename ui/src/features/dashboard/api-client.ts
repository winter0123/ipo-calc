import { apiFetch } from '@/shared/api/client.ts'
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

// 응답이 계약과 다르면 ZodError로 실패해 TanStack Query의 error 상태로 흐른다(UI는 일반 오류 문구).
async function fetchParsed<T>(path: string, schema: z.ZodType<T>, signal?: AbortSignal) {
  return schema.parse(await apiFetch<unknown>(path, { method: 'GET', signal }))
}

export async function getDashboardSummary(signal?: AbortSignal): Promise<DashboardSummary> {
  return fetchParsed('/dashboard/summary', dashboardSummarySchema, signal)
}

export async function getDashboardByInvestor(signal?: AbortSignal): Promise<DashboardByInvestor[]> {
  return fetchParsed('/dashboard/by-investor', z.array(dashboardByInvestorSchema), signal)
}

export async function getDashboardBySecuritiesFirm(
  signal?: AbortSignal,
): Promise<DashboardBySecuritiesFirm[]> {
  return fetchParsed(
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
  return fetchParsed(
    `/dashboard/by-period?${params.toString()}`,
    z.array(dashboardByPeriodSchema),
    signal,
  )
}
