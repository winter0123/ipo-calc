import { apiFetch } from '../../shared/api/client.ts'
import type { Subscription, SubscriptionRegisterRequest } from './types.ts'

export async function createSubscription(data: SubscriptionRegisterRequest): Promise<Subscription> {
  return apiFetch<Subscription>('/subscriptions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
