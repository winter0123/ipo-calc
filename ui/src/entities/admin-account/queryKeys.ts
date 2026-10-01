export const adminAccountKeys = {
  all: ['admin-accounts'] as const,
  list: (pendingOnly: boolean) => [...adminAccountKeys.all, 'list', pendingOnly] as const,
}
