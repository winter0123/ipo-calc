import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { type ImpersonationStart } from './types.ts'
import { impersonationStatusSchema, type ImpersonationStatus } from './types.ts'

/** 임퍼소네이션 시작. 토큰이 없으면 부트스트랩 후 안전하지 않은 요청을 보낸다. */
export async function startImpersonation(accountId: number): Promise<ImpersonationStart> {
  return apiFetch(`/admin/impersonation/start/${accountId}`, { method: 'POST' })
}

/** 임퍼소네이션 상태 조회. 관리자만 호출한다. */
export function impersonationStatus(signal?: AbortSignal): Promise<ImpersonationStatus> {
  return apiGetParsed('/admin/impersonation/status', impersonationStatusSchema, signal)
}

/** 임퍼소네이션 종료 (204). */
export function endImpersonation(): Promise<void> {
  return apiFetch('/admin/impersonation/end', { method: 'POST' })
}
