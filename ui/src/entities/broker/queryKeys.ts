export const brokerKeys = {
  all: ['broker'] as const,
  credential: (securitiesAccountId?: number) =>
    [...brokerKeys.all, 'credential', securitiesAccountId] as const,
  preview: (securitiesAccountId?: number) =>
    [...brokerKeys.all, 'preview', securitiesAccountId] as const,
}
