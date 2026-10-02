export const profitKeys = {
  all: ['profit'] as const,
  investor: (investorId: number | undefined) =>
    [...profitKeys.all, 'investor', investorId] as const,
}
