import { describe, it, expect } from 'vitest'
import type { Ipo } from '@/entities/ipo/index.ts'
import {
  depositFor,
  formatDate,
  ipoPriceInfo,
  sortIposForSubscription,
} from './subscriptionForm.ts'

const ipo = (id: number, start: string, end: string, extra: Partial<Ipo> = {}): Ipo => ({
  id,
  companyName: 'c',
  ipoName: `ipo${id}`,
  stockCode: null,
  roundLabel: null,
  lowerBandPrice: null,
  upperBandPrice: null,
  fixedPrice: null,
  subscriptionStartDate: start,
  subscriptionEndDate: end,
  listingDate: null,
  offeringShareCount: null,
  status: 'x',
  createdAt: '',
  updatedAt: '',
  ...extra,
})

describe('subscriptionForm', () => {
  it('진행 중 → 예정(빠른 순) → 마감(최근 순)으로 정렬한다', () => {
    const list = [
      ipo(1, '2026-10-01', '2026-10-02'),
      ipo(2, '2026-10-20', '2026-10-21'),
      ipo(3, '2026-10-13', '2026-10-14'),
      ipo(4, '2026-10-15', '2026-10-16'),
      ipo(5, '2026-09-20', '2026-09-21'),
    ]
    expect(sortIposForSubscription(list, '2026-10-13').map((i) => i.id)).toEqual([3, 4, 2, 1, 5])
  })

  it('증거금은 확정가(없으면 밴드 상단)×수량×50%를 올림한다', () => {
    expect(depositFor(ipo(1, 'a', 'b', { fixedPrice: 11000 }), 10)).toBe(55000)
    expect(depositFor(ipo(1, 'a', 'b', { upperBandPrice: 12001 }), 1)).toBe(6001)
    expect(depositFor(ipo(1, 'a', 'b'), 10)).toBeUndefined()
    expect(depositFor(ipo(1, 'a', 'b', { fixedPrice: 100 }), Number.NaN)).toBeUndefined()
  })

  it('날짜와 가격 문구', () => {
    expect(formatDate('2026-10-13T09:00')).toBe('2026. 10. 13.')
    expect(ipoPriceInfo(ipo(1, 'a', 'b'))).toBe('공모가 미정')
    expect(ipoPriceInfo(ipo(1, 'a', 'b', { lowerBandPrice: 1000, upperBandPrice: 2000 }))).toBe(
      '희망 공모가 1,000원 ~ 2,000원',
    )
  })
})
