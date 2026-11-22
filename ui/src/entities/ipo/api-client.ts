import { z } from 'zod'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import {
  ipoInsightSchema,
  ipoSchema,
  ipoSyncStatusSchema,
  krxListingPreviewSchema,
} from './types.ts'
import type {
  Ipo,
  IpoInsight,
  IpoRegisterRequest,
  IpoSyncStatus,
  KrxListingApplyRequest,
  KrxListingApplyResult,
  KrxListingPreview,
} from './types.ts'

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
  return apiFetchParsed('/ipos', ipoSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

// DART 목록·상세를 지금 가져온다(관리자). 수십 초~수 분 걸릴 수 있다.
export async function syncIposNow(range?: {
  fromMonth: string
  toMonth: string
}): Promise<IpoSyncStatus> {
  return apiFetchParsed('/ipos/sync', ipoSyncStatusSchema, {
    method: 'POST',
    body: range ? JSON.stringify(range) : undefined,
  })
}

// 동기화 상태를 조회한다(관리자).
export async function getIpoSyncStatus(signal?: AbortSignal): Promise<IpoSyncStatus> {
  return apiGetParsed('/ipos/sync/status', ipoSyncStatusSchema, signal)
}

const krxAutoOutcomeSchema = z.object({
  ipoId: z.number(),
  stockCode: z.string(),
  status: z.string(),
  receiptNo: z.string().nullable(),
  proposedListingDate: z.string().nullable(),
  reason: z.string(),
})

const krxAutoStatusSchema = z.object({
  autoApplyEnabled: z.boolean(),
  lastReport: z
    .object({
      completedAt: z.string(),
      dryRun: z.boolean(),
      counts: z.record(z.string(), z.number()),
      rows: z.array(krxAutoOutcomeSchema),
    })
    .nullable(),
})

export type KrxAutoStatus = z.infer<typeof krxAutoStatusSchema>

/** 관리자 자동 대조 집계와 개별 공고 확인/보류 원인을 읽는다. */
export async function getKrxAutoStatus(signal?: AbortSignal): Promise<KrxAutoStatus> {
  return apiGetParsed('/ipos/krx-listing/auto-status', krxAutoStatusSchema, signal)
}

export async function previewKrxListing(id: number, noticeUrl: string): Promise<KrxListingPreview> {
  return apiFetchParsed(`/ipos/${id}/krx-listing/preview`, krxListingPreviewSchema, {
    method: 'POST',
    body: JSON.stringify({ noticeUrl }),
  })
}

export async function applyKrxListing(
  id: number,
  request: KrxListingApplyRequest,
): Promise<KrxListingApplyResult> {
  return apiFetchParsed(
    `/ipos/${id}/krx-listing/apply`,
    z.object({
      ipoId: z.number(),
      applied: z.boolean(),
      status: z.string(),
      reasons: z.array(z.string()),
    }),
    { method: 'POST', body: JSON.stringify(request) },
  )
}
