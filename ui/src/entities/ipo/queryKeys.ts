export const ipoKeys = {
  all: ['ipos'] as const,
  list: () => [...ipoKeys.all, 'list'] as const,
}
