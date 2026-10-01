import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchInvestors } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
const TS = '2026-01-01T00:00:00'

const INVESTOR = {
  id: 1,
  displayName: 'x',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

describe('investor api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('정상 응답은 통과하고 타입이 어긋나면 거부한다', async () => {
    stub([INVESTOR])
    await expect(fetchInvestors()).resolves.toHaveLength(1)
    stub([{ ...INVESTOR, id: '1' }])
    await expect(fetchInvestors()).rejects.toBeInstanceOf(ZodError)
  })
})
