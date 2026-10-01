export const securitiesFirmKeys = {
  all: ['securities-firms'] as const,
  list: () => [...securitiesFirmKeys.all, 'list'] as const,
}
