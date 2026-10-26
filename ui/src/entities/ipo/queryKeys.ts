export const ipoKeys = {
  all: ['ipos'] as const,
  list: () => [...ipoKeys.all, 'list'] as const,
  detail: (id: number) => [...ipoKeys.all, 'detail', id] as const,
  invalidDetail: () => [...ipoKeys.all, 'detail', 'invalid'] as const,
  insight: (id: number) => [...ipoKeys.all, 'insight', id] as const,
  syncStatus: () => ['ipoSyncStatus'] as const,
}
