import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { ipoInsightSchema, ipoSchema, ipoSyncStatusSchema } from './types.ts'
import type { Ipo, IpoInsight, IpoRegisterRequest, IpoSyncStatus } from './types.ts'

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
export async function syncIposNow(range?: {
  fromMonth: string
  toMonth: string
}): Promise<IpoSyncStatus> {
  const raw = await apiFetch<unknown>('/ipos/sync', {
    method: 'POST',
    body: range ? JSON.stringify(range) : undefined,
  })
  return ipoSyncStatusSchema.parse(raw)
}

// 동기화 상태를 조회한다(관리자).
export async function getIpoSyncStatus(signal?: AbortSignal): Promise<IpoSyncStatus> {
  return apiGetParsed('/ipos/sync/status', ipoSyncStatusSchema, signal)
}
