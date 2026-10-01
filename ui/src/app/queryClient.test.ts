import { describe, it, expect, vi } from 'vitest'
import { ApiError } from '../shared/api/apiError.ts'
import { createQueryClient, shouldRetryQuery } from './queryClient.ts'

describe('shouldRetryQuery', () => {
  it.each([400, 401, 403, 404])('%i 오류는 즉시 실패한다', (status) => {
    expect(shouldRetryQuery(0, new ApiError(status, 'x'))).toBe(false)
  })

  it('5xx와 네트워크 오류는 3회까지 재시도한다', () => {
    expect(shouldRetryQuery(0, new ApiError(500, 'x'))).toBe(true)
    expect(shouldRetryQuery(2, new TypeError('Failed to fetch'))).toBe(true)
    expect(shouldRetryQuery(3, new ApiError(500, 'x'))).toBe(false)
  })
})

describe('createQueryClient', () => {
  it('조회에는 정책을 적용하고 mutation은 재시도하지 않는다', async () => {
    const client = createQueryClient()
    expect(client.getDefaultOptions().queries?.retry).toBe(shouldRetryQuery)

    const mutationFn = vi.fn().mockRejectedValue(new ApiError(500, 'x'))
    await client
      .getMutationCache()
      .build(client, { mutationFn })
      .execute(undefined)
      .catch(() => undefined)
    expect(mutationFn).toHaveBeenCalledTimes(1)
  })
})
