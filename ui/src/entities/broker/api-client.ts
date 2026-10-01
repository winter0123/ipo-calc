import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import {
  brokerCredentialSchema,
  syncPreviewSchema,
  type BrokerCredential,
  type BrokerCredentialRegisterRequest,
  type SyncConfirmSale,
  type SyncPreview,
} from './types.ts'

const base = (securitiesAccountId: number) => `/securities-accounts/${securitiesAccountId}`

export function fetchBrokerCredential(
  securitiesAccountId: number,
  signal?: AbortSignal,
): Promise<BrokerCredential> {
  return apiGetParsed(`${base(securitiesAccountId)}/api-credential`, brokerCredentialSchema, signal)
}

export function registerBrokerCredential(
  securitiesAccountId: number,
  data: BrokerCredentialRegisterRequest,
): Promise<BrokerCredential> {
  return apiFetch<BrokerCredential>(`${base(securitiesAccountId)}/api-credential`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function disconnectBrokerCredential(securitiesAccountId: number): Promise<void> {
  return apiFetch<void>(`${base(securitiesAccountId)}/api-credential`, { method: 'DELETE' })
}

export function fetchSyncPreview(
  securitiesAccountId: number,
  signal?: AbortSignal,
): Promise<SyncPreview> {
  return apiGetParsed(`${base(securitiesAccountId)}/broker-sync/preview`, syncPreviewSchema, signal)
}

export function confirmSync(securitiesAccountId: number, sales: SyncConfirmSale[]): Promise<void> {
  return apiFetch<void>(`${base(securitiesAccountId)}/broker-sync/confirm`, {
    method: 'POST',
    body: JSON.stringify({ sales }),
  })
}
