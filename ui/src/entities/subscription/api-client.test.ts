import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { getInvestmentHistory, getSubscriptionDetail } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
const TS = '2026-01-01T00:00:00'

const ITEM = {
  id: 1,
  subscribedAt: TS,
  ipoId: 1,
  ipoName: null,
  ipoFixedPrice: null,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [
    {
      id: 1,
      quantity: 1,
      amount: null,
      fee: null,
      tax: null,
      otherCost: null,
      status: 'ACTIVE',
      soldAt: TS,
    },
  ],
  remainingQuantity: null,
  investorName: null,
  securitiesFirmName: null,
}

describe('subscription api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('목록: 매도 금액·수수료·세금, 배정, 투자자명 null을 허용하고 숫자 어긋남은 거부한다', async () => {
    const page = { content: [ITEM], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    stub(page)
    await expect(getInvestmentHistory()).resolves.toEqual(page)
    stub({ ...page, totalElements: '1' })
    await expect(getInvestmentHistory()).rejects.toBeInstanceOf(ZodError)
  })

  it('상세: 단건도 같은 스키마로 검증한다', async () => {
    stub(ITEM)
    await expect(getSubscriptionDetail(1)).resolves.toEqual(ITEM)
    stub({ ...ITEM, sales: 'none' })
    await expect(getSubscriptionDetail(1)).rejects.toBeInstanceOf(ZodError)
  })
})
