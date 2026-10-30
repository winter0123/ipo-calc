export const impersonationKeys = {
  all: ['impersonation'] as const,
  status: () => [...impersonationKeys.all, 'status'] as const,
}
