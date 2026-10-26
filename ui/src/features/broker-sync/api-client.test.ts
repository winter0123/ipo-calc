import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { confirmBrokerSync } from './api-client.ts'

const SALE = {
  id: 8,
  allocationId: 30,
  quantity: 10,
  price: 10000,
  amount: 100000,
  saleDate: '2026-10-26T00:00:00',
  fee: 2000,
  tax: 400,
  otherCost: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-10-26T00:00:00',
  updatedAt: '2026-10-26T00:00:00',
}

describe('broker sync confirm api-client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('저장된 매도 목록 응답을 검증하고 기존 void 반환을 유지한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify([SALE]))))
    vi.stubGlobal('fetch', fetchMock)
    const sales = [
      {
        allocationId: 30,
        quantity: 10,
        price: 10000,
        amount: 100000,
        fee: 2000,
        tax: 400,
        saleDate: '2026-10-26T00:00:00',
      },
    ]

    await expect(confirmBrokerSync(10, sales)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/securities-accounts\/10\/broker-sync\/confirm$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ sales }) }),
    )
  })

  it('불완전한 매도 응답을 거부한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(JSON.stringify([{ id: 8 }])))),
    )

    await expect(confirmBrokerSync(10, [])).rejects.toBeInstanceOf(ZodError)
  })
})
