import { describe, it, expect } from 'vitest'
import { groupProfits } from './groupProfits.ts'

const sale = (
  saleDate: string,
  listingDate: string | null,
  profit: number,
  saleAmount: number | null = 1000,
) => ({ saleDate, listingDate, saleAmount, afterTaxProfit: profit })

const SALES = [
  sale('2026-11-03T10:00:00', '2026-10-15', 500), // 10월 상장, 11월 매도 → 10월
  sale('2026-10-20T10:00:00', '2026-10-15', -200, null),
  sale('2026-12-05T10:00:00', null, 300), // 상장일 없음 → 매도일
]

describe('groupProfits', () => {
  it('월 단위: 상장일 우선·매도일 대체로 묶고 빈 달은 0으로 채운다', () => {
    const r = groupProfits(SALES, 'month')
    expect(r.map((g) => [g.label, g.count, g.saleAmount, g.profit])).toEqual([
      ['2026. 10.', 2, 1000, 300],
      ['2026. 11.', 0, 0, 0],
      ['2026. 12.', 1, 1000, 300],
    ])
  })

  it('조회 기간(from~to)까지 채운다', () => {
    const r = groupProfits(SALES, 'month', '2026-09-01', '2027-01-31')
    expect(r.map((g) => g.label)).toEqual([
      '2026. 9.',
      '2026. 10.',
      '2026. 11.',
      '2026. 12.',
      '2027. 1.',
    ])
  })

  it('연 단위는 해를 건너뛰어도 채운다', () => {
    const r = groupProfits([sale('2024-03-01', null, 1), sale('2026-03-01', null, 2)], 'year')
    expect(r.map((g) => [g.label, g.profit])).toEqual([
      ['2024년', 1],
      ['2025년', 0],
      ['2026년', 2],
    ])
  })

  it('일 단위는 데이터가 있는 날만, 상장일 기준이다', () => {
    const r = groupProfits(SALES, 'day', '2026-10-01', '2026-12-31')
    expect(r.map((g) => [g.label, g.title, g.profit])).toEqual([
      ['10. 15.', '2026. 10. 15.', 300],
      ['12. 5.', '2026. 12. 5.', 300],
    ])
  })

  it('데이터가 없으면 빈 배열', () => {
    expect(groupProfits([], 'month', '2026-01-01', '2026-12-31')).toEqual([])
  })
})
