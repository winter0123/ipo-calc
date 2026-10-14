import { z } from 'zod'

export const vapidKeySchema = z.object({
  enabled: z.boolean(),
  publicKey: z.string().nullable(),
})
export type VapidKey = z.infer<typeof vapidKeySchema>

export const preferencesSchema = z.object({
  subscriptionDeadline: z.boolean(),
  listingDay: z.boolean(),
})
export type NotificationPreferences = z.infer<typeof preferencesSchema>

export const testResultSchema = z.object({ sent: z.number() })

/** 브라우저 PushSubscription.toJSON() 중 서버가 받는 부분 */
export interface PushSubscriptionPayload {
  endpoint: string
  keys: { p256dh: string; auth: string }
}
