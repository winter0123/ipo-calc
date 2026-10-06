import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { getInvestorProfits } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
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
    }
    stub(investor)
    await expect(getInvestorProfits(1)).resolves.toEqual(investor)
    stub({ ...investor, totalSaleAmount: undefined })
    await expect(getInvestorProfits(1)).rejects.toBeInstanceOf(ZodError)
  })
})
