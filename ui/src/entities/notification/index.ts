export type { NotificationPreferences, PushSubscriptionPayload } from './types.ts'
export { usePreferences, useSavePreferences, useVapidKey } from './useNotification.ts'
export {
  registerPushSubscription,
  removePushSubscription,
  sendTestNotification,
} from './api-client.ts'
export { notificationKeys } from './queryKeys.ts'
