import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import {
  preferencesSchema,
  successResponseSchema,
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
  return apiFetchParsed('/notifications/preferences', preferencesSchema, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function registerPushSubscription(data: PushSubscriptionPayload): Promise<void> {
  return apiFetchParsed('/notifications/subscriptions', successResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  }).then(() => undefined)
}

export function removePushSubscription(endpoint: string): Promise<void> {
  return apiFetchParsed('/notifications/subscriptions', successResponseSchema, {
    method: 'DELETE',
    body: JSON.stringify({ endpoint }),
  }).then(() => undefined)
}

export async function sendTestNotification(): Promise<number> {
  const result = await apiFetchParsed('/notifications/test', testResultSchema, { method: 'POST' })
  return result.sent
}
