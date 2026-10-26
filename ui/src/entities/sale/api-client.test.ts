import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { createSale, getSale, updateSale } from './api-client.ts'

const TS = '2026-10-01T09:00:00'
const SALE = {
  id: 1,
  allocationId: 2,
  quantity: 0,
  price: null,
  amount: null,
  saleDate: TS,
  fee: null,
  tax: null,
  otherCost: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

describe('getSale', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('GET 응답을 DTO schema로 검증하고 취소 신호를 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(SALE)))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(getSale(1, controller.signal)).resolves.toEqual(SALE)

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/sales\/1$/),
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    )
  })

  it('필수 필드가 빠진 응답은 ZodError로 거부한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }))))

    await expect(getSale(1)).rejects.toBeInstanceOf(ZodError)
  })
})

describe('sale mutations', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('POST/PATCH 응답을 sale schema로 검증한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(SALE))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createSale({ allocationId: 2, quantity: 1, saleDate: TS })).resolves.toEqual(SALE)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/sales$/),
      expect.objectContaining({ method: 'POST' }),
    )

    await expect(updateSale(1, { quantity: 1 })).resolves.toEqual(SALE)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/sales\/1$/),
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('잘못된 mutation 응답은 ZodError로 거부한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }))))

    await expect(createSale({ allocationId: 2, quantity: 1, saleDate: TS })).rejects.toBeInstanceOf(
      ZodError,
    )
  })
})
