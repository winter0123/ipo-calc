import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchInvestors, registerInvestor } from './api-client.ts'

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

  it('등록 응답도 investor schema로 검증한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(INVESTOR))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(registerInvestor({ displayName: 'x' })).resolves.toEqual(INVESTOR)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/investors$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ displayName: 'x' }) }),
    )

    stub({ ...INVESTOR, accountId: 'invalid' })
    await expect(registerInvestor({ displayName: 'x' })).rejects.toBeInstanceOf(ZodError)
  })
})
