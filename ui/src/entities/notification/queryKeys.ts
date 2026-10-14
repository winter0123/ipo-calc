export const notificationKeys = {
  all: ['notifications'] as const,
  vapidKey: () => [...notificationKeys.all, 'vapid-key'] as const,
  preferences: () => [...notificationKeys.all, 'preferences'] as const,
}
