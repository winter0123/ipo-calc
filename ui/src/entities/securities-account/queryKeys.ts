export const securitiesAccountKeys = {
  all: ['securities-accounts'] as const,
  list: (investorId?: number) => [...securitiesAccountKeys.all, 'list', investorId] as const,
}
