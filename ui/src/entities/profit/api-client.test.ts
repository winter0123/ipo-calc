import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { getInvestorProfits } from './api-client.ts'

const stub = (body: unknown) => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body)))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
const TS = '2026-01-01T00:00:00'

const SALE_PROFIT = {
  saleId: 1,
  saleDate: TS,
  allocationId: 1,
  quantity: 1,
  saleAmount: null,
  matchedAcquisitionAmount: 0,
  saleFee: 0,
  saleTax: 0,
  saleOtherCost: 0,
  preTaxProfit: 0,
  afterTaxProfit: 0,
  realizedProfit: 0,
  listingDate: null,
  ipoName: null,
}

describe('profit api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('매도금액 null을 허용한다', async () => {
    const investor = {
      investorId: 1,
      sales: [SALE_PROFIT],
      totalSaleAmount: 0,
      totalPreTaxProfit: 0,
      totalAfterTaxProfit: 0,
      totalRealizedProfit: 0,
    }
    stub(investor)
    await expect(getInvestorProfits(1)).resolves.toEqual(investor)
  })

  it('투자자 수익은 합계가 누락되면 거부한다', async () => {
    const investor = {
      investorId: 1,
      sales: [SALE_PROFIT],
      totalSaleAmount: 0,
      totalPreTaxProfit: 0,
      totalAfterTaxProfit: 0,
      totalRealizedProfit: 0,
    }
    stub(investor)
    await expect(getInvestorProfits(1)).resolves.toEqual(investor)
    stub({ ...investor, totalSaleAmount: undefined })
    await expect(getInvestorProfits(1)).rejects.toBeInstanceOf(ZodError)
  })
})

const EMPTY_INVESTOR = {
  investorId: 1,
  sales: [],
  totalSaleAmount: 0,
  totalPreTaxProfit: 0,
  totalAfterTaxProfit: 0,
  totalRealizedProfit: 0,
}

const lastRequest = (fetchMock: ReturnType<typeof stub>) => {
  const raw = String(fetchMock.mock.calls[0][0])
  return new URL(raw.startsWith('http') ? raw : `http://localhost${raw}`)
}

describe('profit api-client 기간 쿼리', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('기간을 주면 from·to 쿼리를 붙인다', async () => {
    const fetchMock = stub(EMPTY_INVESTOR)
    await getInvestorProfits(1, { from: '2026-09-01', to: '2026-09-30' })
    const url = lastRequest(fetchMock)
    expect(url.pathname).toBe('/investors/1/profits')
    expect(url.search).toBe('?from=2026-09-01&to=2026-09-30')
  })

  it('기간을 생략하면 쿼리를 붙이지 않는다', async () => {
    const fetchMock = stub(EMPTY_INVESTOR)
    await getInvestorProfits(7)
    const url = lastRequest(fetchMock)
    expect(url.pathname).toBe('/investors/7/profits')
    expect(url.search).toBe('')
  })

  it('기간 중 값이 있는 것만 붙인다', async () => {
    const fromOnly = stub(EMPTY_INVESTOR)
    await getInvestorProfits(1, { from: '2026-09-01' })
    expect(lastRequest(fromOnly).search).toBe('?from=2026-09-01')

    const toOnly = stub(EMPTY_INVESTOR)
    await getInvestorProfits(1, { to: '2026-09-30' })
    expect(lastRequest(toOnly).search).toBe('?to=2026-09-30')

    const empty = stub(EMPTY_INVESTOR)
    await getInvestorProfits(1, {})
    expect(lastRequest(empty).search).toBe('')
  })

  it('signal을 함께 전달한다', async () => {
    const fetchMock = stub(EMPTY_INVESTOR)
    const controller = new AbortController()
    await getInvestorProfits(1, { from: '2026-09-01' }, controller.signal)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/investors/1/profits?from=2026-09-01'),
      expect.objectContaining({ signal: controller.signal }),
    )
  })
})
