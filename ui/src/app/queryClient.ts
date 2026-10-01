import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/apiError.ts'

const MAX_QUERY_RETRIES = 3

// 4xx는 재시도해도 결과가 같으므로 즉시 실패, 네트워크·5xx만 재시도한다.
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < MAX_QUERY_RETRIES
}

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: { skipUnauthorizedRedirect?: boolean }
  }
}

interface QueryClientOptions {
  onUnauthorized?: (client: QueryClient) => void
}

// mutations.retry는 설정하지 않는다(기본 0) — 금융 등록 요청의 이중 제출 방지.
// 로그인 요청 자체의 401(잘못된 자격 증명)은 meta.skipUnauthorizedRedirect로 제외한다.
export function createQueryClient({ onUnauthorized }: QueryClientOptions = {}): QueryClient {
  const handle = (error: unknown, skip = false) => {
    if (!skip && error instanceof ApiError && error.status === 401) onUnauthorized?.(client)
  }
  const client = new QueryClient({
    queryCache: new QueryCache({ onError: (error) => handle(error) }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) =>
        handle(error, mutation.meta?.skipUnauthorizedRedirect),
    }),
    defaultOptions: { queries: { retry: shouldRetryQuery } },
  })
  return client
}
