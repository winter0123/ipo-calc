export const profitKeys = {
  all: ['profit'] as const,
  investor: (investorId: number | undefined, from?: string | null, to?: string | null) =>
    [...profitKeys.all, 'investor', investorId, from ?? null, to ?? null] as const,
}
