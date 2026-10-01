export const ipoKeys = {
  all: ['ipos'] as const,
  list: () => [...ipoKeys.all, 'list'] as const,
  detail: (id: number) => [...ipoKeys.all, 'detail', id] as const,
}
