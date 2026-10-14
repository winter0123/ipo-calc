import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { ipoInsightSchema, ipoSchema, ipoSyncResultSchema } from './types.ts'
import type { Ipo, IpoInsight, IpoRegisterRequest, IpoSyncResult } from './types.ts'

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

// DART 목록·상세를 지금 가져온다(관리자). 수십 초~수 분 걸릴 수 있다.
export async function syncIposNow(): Promise<IpoSyncResult> {
  const raw = await apiFetch<unknown>('/ipos/sync', { method: 'POST' })
  return ipoSyncResultSchema.parse(raw)
}
