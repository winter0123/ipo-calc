export const brokerKeys = {
  all: ['broker'] as const,
  credential: (securitiesAccountId?: number) =>
    [...brokerKeys.all, 'credential', securitiesAccountId] as const,
  preview: (securitiesAccountId?: number, range?: { from: string; to: string }) =>
    [...brokerKeys.all, 'preview', securitiesAccountId, range?.from, range?.to] as const,
}
