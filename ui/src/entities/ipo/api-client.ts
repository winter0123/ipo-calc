import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { ipoInsightSchema, ipoSchema } from './types.ts'
import type { Ipo, IpoInsight, IpoRegisterRequest } from './types.ts'

export async function fetchIpos(signal?: AbortSignal): Promise<Ipo[]> {
  return apiGetParsed('/ipos', z.array(ipoSchema), signal)
}

export async function fetchIpo(id: number, signal?: AbortSignal): Promise<Ipo> {
  return apiGetParsed(`/ipos/${id}`, ipoSchema, signal)
}

export async function fetchIpoInsight(id: number, signal?: AbortSignal): Promise<IpoInsight> {
  return apiGetParsed(`/ipos/${id}/insight`, ipoInsightSchema, signal)
}

export async function registerIpo(data: IpoRegisterRequest): Promise<Ipo> {
  return apiFetch<Ipo>('/ipos', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export interface IpoImportResult {
  successCount: number
  failureCount: number
  failures: { rowNumber: number; reason: string }[]
}

export async function importIposCsv(file: File): Promise<IpoImportResult> {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<IpoImportResult>('/ipos/import', { method: 'POST', body })
}
