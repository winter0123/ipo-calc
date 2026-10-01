import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../shared/api/apiError.ts'

const MAX_QUERY_RETRIES = 3

// 4xx는 재시도해도 결과가 같으므로 즉시 실패, 네트워크·5xx만 재시도한다.
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < MAX_QUERY_RETRIES
}

// mutations.retry는 설정하지 않는다(기본 0) — 금융 등록 요청의 이중 제출 방지.
export function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetryQuery } } })
}
