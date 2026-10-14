import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import {
  preferencesSchema,
  testResultSchema,
  vapidKeySchema,
  type NotificationPreferences,
  type PushSubscriptionPayload,
  type VapidKey,
} from './types.ts'

export function fetchVapidKey(signal?: AbortSignal): Promise<VapidKey> {
  return apiGetParsed('/notifications/vapid-public-key', vapidKeySchema, signal)
}

export function fetchPreferences(signal?: AbortSignal): Promise<NotificationPreferences> {
  return apiGetParsed('/notifications/preferences', preferencesSchema, signal)
}

export async function savePreferences(
  data: NotificationPreferences,
): Promise<NotificationPreferences> {
  const body = await apiFetch<unknown>('/notifications/preferences', {
    method: 'PUT',
    body: JSON.stringify(data),
  })
  return preferencesSchema.parse(body)
}

export function registerPushSubscription(data: PushSubscriptionPayload): Promise<void> {
  return apiFetch<void>('/notifications/subscriptions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function removePushSubscription(endpoint: string): Promise<void> {
  return apiFetch<void>('/notifications/subscriptions', {
    method: 'DELETE',
    body: JSON.stringify({ endpoint }),
  })
}

export async function sendTestNotification(): Promise<number> {
  const body = await apiFetch<unknown>('/notifications/test', { method: 'POST' })
  return testResultSchema.parse(body).sent
}
