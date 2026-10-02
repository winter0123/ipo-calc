import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchMarketHolidays } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))

describe('market-holiday api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('GET /market-holidays?year=2026을 호출하고 파싱된 배열을 반환한다', async () => {
    const holidays = [
      { date: '2026-01-01', name: '신정' },
      { date: '2026-10-09', name: '한글날' },
    ]
    stub(holidays)
    await expect(fetchMarketHolidays(2026)).resolves.toEqual(holidays)
  })

  it('빈 배열 응답도 통과한다', async () => {
    stub([])
    await expect(fetchMarketHolidays(2026)).resolves.toEqual([])
  })

  it('date 또는 name이 없으면 ZodError로 거부한다', async () => {
    stub([{ date: '2026-01-01' }])
    await expect(fetchMarketHolidays(2026)).rejects.toBeInstanceOf(ZodError)
  })

  it('year 파라미터가 URL에 포함된다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([])))
    vi.stubGlobal('fetch', fetchMock)
    await fetchMarketHolidays(2027)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/market-holidays?year=2027'),
      expect.any(Object),
    )
    vi.unstubAllGlobals()
  })
})
