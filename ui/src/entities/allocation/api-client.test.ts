import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { createAllocation, getAllocation, updateAllocation } from './api-client.ts'

const TS = '2026-10-01T09:00:00'
const ALLOCATION = {
  id: 1,
  subscriptionId: 2,
  allocatedQuantity: 0,
  allocatedAt: TS,
  allocationMethod: null,
  allocationPrice: null,
  note: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

describe('getAllocation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('GET 응답을 DTO schema로 검증하고 취소 신호를 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(ALLOCATION)))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(getAllocation(1, controller.signal)).resolves.toEqual(ALLOCATION)

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/allocations\/1$/),
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    )
  })

  it('필수 필드가 빠진 응답은 ZodError로 거부한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }))))

    await expect(getAllocation(1)).rejects.toBeInstanceOf(ZodError)
  })
})

describe('allocation mutations', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('POST/PATCH 응답을 allocation schema로 검증한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(ALLOCATION))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      createAllocation({ subscriptionId: 2, allocatedQuantity: 0, allocatedAt: TS }),
    ).resolves.toEqual(ALLOCATION)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/allocations$/),
      expect.objectContaining({ method: 'POST' }),
    )

    await expect(updateAllocation(1, { allocatedQuantity: 0 })).resolves.toEqual(ALLOCATION)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/allocations\/1$/),
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('잘못된 mutation 응답은 ZodError로 거부한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }))))

    await expect(
      createAllocation({ subscriptionId: 2, allocatedQuantity: 0, allocatedAt: TS }),
    ).rejects.toBeInstanceOf(ZodError)
  })
})
