export const investorKeys = {
  all: ['investors'] as const,
  list: () => [...investorKeys.all, 'list'] as const,
}
