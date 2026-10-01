import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { getDashboardByPeriod, getDashboardSummary } from './api-client.ts'

const SUMMARY = {
  totalInvestmentAmount: 1000,
  totalPreTaxProfit: 100,
  totalAfterTaxProfit: 80,
  totalProfitRate: 8,
  participationCount: 2,
}

describe('dashboard api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  const stub = (body: unknown) =>
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))

  it('계약과 일치하는 응답은 그대로 반환한다', async () => {
    stub(SUMMARY)
    await expect(getDashboardSummary()).resolves.toEqual(SUMMARY)
  })

  it('필드 타입이 어긋난 응답은 ZodError로 거부한다', async () => {
    stub({ ...SUMMARY, totalInvestmentAmount: '1000' })
    await expect(getDashboardSummary()).rejects.toBeInstanceOf(ZodError)
  })

  it('배열 응답의 항목이 어긋나면 거부하고, 정상이면 통과한다', async () => {
    stub([{ ...SUMMARY, period: '2025-03' }])
    await expect(getDashboardByPeriod('month', 2025, 3)).resolves.toHaveLength(1)
    stub([{ ...SUMMARY }])
    await expect(getDashboardByPeriod('month')).rejects.toBeInstanceOf(ZodError)
  })
})
