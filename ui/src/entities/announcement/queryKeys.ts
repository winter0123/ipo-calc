export const announcementKeys = {
  list: (page: number) => ['announcements', page] as const,
  detail: (id: number) => ['announcement', id] as const,
  popup: (accountId: number | undefined) => ['announcement-popup', accountId] as const,
  adminList: (page: number, status: '' | 'ACTIVE' | 'CANCELLED') =>
    ['admin-announcements', page, status] as const,
  adminAll: ['admin-announcements'] as const,
}
