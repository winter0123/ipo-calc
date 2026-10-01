import { z } from 'zod'
import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { adminAccountSchema, type AdminAccount } from './types.ts'

export function fetchAdminAccounts(
  pendingOnly: boolean,
  signal?: AbortSignal,
): Promise<AdminAccount[]> {
  const query = pendingOnly ? '?approved=false' : ''
  return apiGetParsed(`/admin/accounts${query}`, z.array(adminAccountSchema), signal)
}

export function approveAccount(accountId: number): Promise<void> {
  return apiFetch<void>(`/admin/accounts/${accountId}/approve`, { method: 'POST' })
}

export function revokeAccount(accountId: number): Promise<void> {
  return apiFetch<void>(`/admin/accounts/${accountId}/revoke`, { method: 'POST' })
}
