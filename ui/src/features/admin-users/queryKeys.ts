export const adminUserKeys = {
  all: ['admin-users'] as const,
  list: (params: { page: number; size: number; search?: string | null }) =>
    [...adminUserKeys.all, 'list', params] as const,
  detail: (id: number) => [...adminUserKeys.all, 'detail', id] as const,
  investors: (id: number) => [...adminUserKeys.all, 'investors', id] as const,
  subscriptions: (id: number) => [...adminUserKeys.all, 'subscriptions', id] as const,
  allocations: (id: number) => [...adminUserKeys.all, 'allocations', id] as const,
  sales: (id: number) => [...adminUserKeys.all, 'sales', id] as const,
}
