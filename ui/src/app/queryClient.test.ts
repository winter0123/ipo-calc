import { describe, it, expect, vi } from 'vitest'
import { z } from 'zod'
import { ApiError } from '../shared/api/apiError.ts'
import { createQueryClient, shouldRetryQuery } from './queryClient.ts'

describe('shouldRetryQuery', () => {
  it.each([400, 401, 403, 404])('%i 오류는 즉시 실패한다', (status) => {
    expect(shouldRetryQuery(0, new ApiError(status, 'x'))).toBe(false)
  })

  it('응답 스키마 불일치(ZodError)는 재시도하지 않는다', () => {
    const result = z.object({ a: z.string() }).safeParse({})
    expect(result.success).toBe(false)
    if (!result.success) expect(shouldRetryQuery(0, result.error)).toBe(false)
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

describe('401 처리', () => {
  const run = (client: ReturnType<typeof createQueryClient>, status: number, meta = {}) =>
    client
      .getMutationCache()
      .build(client, { mutationFn: () => Promise.reject(new ApiError(status, 'x')), meta })
      .execute(undefined)
      .catch(() => undefined)

  it('조회·mutation 401은 onUnauthorized를 호출한다', async () => {
    const onUnauthorized = vi.fn()
    const client = createQueryClient({ onUnauthorized })
    await client
      .fetchQuery({ queryKey: ['x'], queryFn: () => Promise.reject(new ApiError(401, 'x')) })
      .catch(() => undefined)
    await run(client, 401)
    expect(onUnauthorized).toHaveBeenCalledTimes(2)
  })

  it('401이 아니거나 skipUnauthorizedRedirect meta가 있으면 호출하지 않는다', async () => {
    const onUnauthorized = vi.fn()
    const client = createQueryClient({ onUnauthorized })
    await run(client, 500)
    await run(client, 401, { skipUnauthorizedRedirect: true })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
