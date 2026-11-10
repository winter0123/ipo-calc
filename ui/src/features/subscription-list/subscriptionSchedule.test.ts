import { describe, expect, it } from 'vitest'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import {
  aggregateSubscriptionSchedule,
  currentMonthRange,
  filterIposBySubscriptionStart,
  parseDateRange,
  updateDateRangeParams,
  paginateItems,
  getTotalPages,
  clampPage,
  getCurrentPhase,
  getPhaseLabel,
} from './subscriptionSchedule.ts'

const ipo = (id: number, start: string, overrides: Partial<Ipo> = {}): Ipo => ({
  id,
  companyName: '회사',
  ipoName: `공모주 ${id}`,
  stockCode: null,
  roundLabel: null,
  lowerBandPrice: null,
  upperBandPrice: null,
  fixedPrice: null,
  listingOpenPrice: null,
  subscriptionStartDate: start,
  subscriptionEndDate: start,
  listingDate: null,
  refundDate: null,
  refundDateEstimated: null,
  offeringShareCount: null,
  status: 'ACTIVE',
  createdAt: '',
  updatedAt: '',
  ...overrides,
})

const item = (
  id: number,
  allocation: InvestmentHistoryItem['allocation'],
): InvestmentHistoryItem => ({
  id,
  subscribedAt: '2026-10-01',
  ipoId: 1,
  ipoName: null,
  ipoFixedPrice: null,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation,
  sales: [],
  remainingQuantity: null,
  investorName: null,
  securitiesFirmName: null,
})

describe('subscription schedule helpers', () => {
  it('uses local month boundaries and falls back for invalid URL ranges', () => {
    expect(currentMonthRange(new Date(2026, 1, 8))).toEqual({
      from: '2026-02-01',
      to: '2026-02-28',
    })
    expect(parseDateRange('2026-11-01', '2026-10-01')).toEqual(currentMonthRange())
    expect(parseDateRange('2026-02-30', '2026-03-01')).toEqual(currentMonthRange())
    expect(parseDateRange('2024-02-29', '2024-03-01')).toEqual({
      from: '2024-02-29',
      to: '2024-03-01',
    })
    expect(parseDateRange('2025-02-29', '2025-03-01')).toEqual(currentMonthRange())
  })

  it('updates one URL boundary, preserves other query values and keeps the range ordered', () => {
    const params = new URLSearchParams('from=2026-10-01&tab=upcoming')
    expect(
      updateDateRangeParams(params, 'to', '2026-09-20', {
        from: '2026-10-01',
        to: '2026-10-31',
      }).toString(),
    ).toBe('from=2026-09-20&tab=upcoming&to=2026-09-20')
    expect(params.toString()).toBe('from=2026-10-01&tab=upcoming')
  })

  it('filters by subscription start date and sorts ascending', () => {
    const result = filterIposBySubscriptionStart(
      [ipo(2, '2026-10-20'), ipo(3, '2026-11-01'), ipo(1, '2026-10-02')],
      { from: '2026-10-01', to: '2026-10-31' },
    )
    expect(result.map(({ id }) => id)).toEqual([1, 2])
  })

  it('distinguishes before, pending, partial, allocated and confirmed zero allocation', () => {
    const result = aggregateSubscriptionSchedule(
      [
        ipo(1, '2026-10-01'),
        ipo(2, '2026-10-02'),
        ipo(3, '2026-10-03'),
        ipo(4, '2026-10-04'),
        ipo(5, '2026-10-05'),
      ],
      [
        { ...item(1, null), ipoId: 1 },
        { ...item(2, null), ipoId: 2 },
        {
          ...item(3, {
            id: 3,
            allocatedQuantity: 2,
            allocatedAmount: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 3,
        },
        {
          ...item(4, {
            id: 4,
            allocatedQuantity: 3,
            allocatedAmount: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 4,
        },
        {
          ...item(5, {
            id: 5,
            allocatedQuantity: 0,
            allocatedAmount: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 5,
        },
      ],
    )
    const partial = aggregateSubscriptionSchedule(
      [ipo(1, '2026-10-01')],
      [
        { ...item(1, null), ipoId: 1 },
        {
          ...item(2, {
            id: 2,
            allocatedQuantity: 1,
            allocatedAmount: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 1,
        },
      ],
    )[0]
    expect(result.map(({ status }) => status)).toEqual([
      'pending',
      'pending',
      'allocated',
      'allocated',
      'unallocated',
    ])
    expect(partial.status).toBe('partial')
    expect(result[0].subscriptionQuantity).toBe(100)
  })

  it('한 공모주의 여러 계좌 청약 기록을 입력 순서대로 모두 유지한다', () => {
    const result = aggregateSubscriptionSchedule(
      [ipo(1, '2026-10-01')],
      [item(21, null), item(22, null)].map((record) => ({ ...record, ipoId: 1 })),
    )

    expect(result[0].subscriptions.map(({ id }) => id)).toEqual([21, 22])
    expect(result[0].subscriptionCount).toBe(2)
  })

  it('paginates items with PAGE_SIZE=10', () => {
    const items = Array.from({ length: 25 }, (_, i) => i + 1)
    expect(paginateItems(items, 0)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(paginateItems(items, 1)).toEqual([11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
    expect(paginateItems(items, 2)).toEqual([21, 22, 23, 24, 25])
    expect(paginateItems(items, 3)).toEqual([])
  })

  it('calculates total pages correctly', () => {
    expect(getTotalPages([])).toBe(0)
    expect(getTotalPages([1, 2, 3])).toBe(1)
    expect(getTotalPages(Array.from({ length: 10 }, (_, i) => i))).toBe(1)
    expect(getTotalPages(Array.from({ length: 11 }, (_, i) => i))).toBe(2)
    expect(getTotalPages(Array.from({ length: 25 }, (_, i) => i))).toBe(3)
  })

  it('clamps page to valid range', () => {
    expect(clampPage(-1, 3)).toBe(0)
    expect(clampPage(0, 3)).toBe(0)
    expect(clampPage(1, 3)).toBe(1)
    expect(clampPage(2, 3)).toBe(2)
    expect(clampPage(3, 3)).toBe(2)
    expect(clampPage(100, 3)).toBe(2)
    expect(clampPage(0, 0)).toBe(0)
    expect(clampPage(5, 0)).toBe(0)
  })

  it('getCurrentPhase returns correct phase based on dates', () => {
    const today = new Date('2026-10-15')

    // 청약 예정: today < subscriptionStart
    expect(getCurrentPhase(ipo(1, '2026-10-20'), today)).toBe('scheduled')

    // 청약 진행 중: subscriptionStart <= today <= subscriptionEnd
    expect(
      getCurrentPhase(ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-20' }), today),
    ).toBe('subscribing')

    // 상장 예정: subscriptionEnd < today, no listingDate
    expect(
      getCurrentPhase(ipo(1, '2026-10-01', { subscriptionEndDate: '2026-10-05' }), today),
    ).toBe('listing-unknown')

    // 상장일: today === listingDate
    expect(getCurrentPhase(ipo(1, '2026-10-01', { listingDate: '2026-10-15' }), today)).toBe(
      'listed',
    )

    // 상장 완료: today > listingDate
    expect(getCurrentPhase(ipo(1, '2026-10-01', { listingDate: '2026-10-10' }), today)).toBe(
      'listed',
    )

    // listingDate가 있는데 subscriptionEnd 이후인 경우 (청약 종료 후, 상장일 미래)
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-01', { subscriptionEndDate: '2026-10-05', listingDate: '2026-10-20' }),
        today,
      ),
    ).toBe('listing-scheduled')

    // listingDate가 미래인데 subscriptionStart도 미래인 경우 → 청약 예정
    expect(
      getCurrentPhase(
        ipo(1, '2026-11-01', { subscriptionEndDate: '2026-11-05', listingDate: '2026-11-15' }),
        today,
      ),
    ).toBe('scheduled')
  })

  it('getCurrentPhase handles subscription boundaries correctly', () => {
    // subscriptionStart 경계: today === subscriptionStart → subscribing
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-15', { subscriptionEndDate: '2026-10-20' }),
        new Date('2026-10-15'),
      ),
    ).toBe('subscribing')

    // subscriptionEnd 경계: today === subscriptionEnd → subscribing
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-15' }),
        new Date('2026-10-15'),
      ),
    ).toBe('subscribing')

    // subscriptionEnd 다음 날: today === subscriptionEnd + 1일, listingDate 없음 → listing-scheduled
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-14' }),
        new Date('2026-10-15'),
      ),
    ).toBe('listing-unknown')

    // subscriptionEnd 다음 날, listingDate 미래 → listing-scheduled
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-14', listingDate: '2026-10-20' }),
        new Date('2026-10-15'),
      ),
    ).toBe('listing-scheduled')

    // subscriptionEnd 다음 날, listingDate 당일 → listing-day
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-14', listingDate: '2026-10-15' }),
        new Date('2026-10-15'),
      ),
    ).toBe('listed')

    // subscriptionEnd 다음 날, listingDate 과거 → listed
    expect(
      getCurrentPhase(
        ipo(1, '2026-10-10', { subscriptionEndDate: '2026-10-14', listingDate: '2026-10-10' }),
        new Date('2026-10-15'),
      ),
    ).toBe('listed')
  })

  it('한국 자정에 상장 완료로 전환한다', () => {
    const target = ipo(1, '2026-11-02', {
      subscriptionEndDate: '2026-11-03',
      listingDate: '2026-11-10',
    })
    expect(getCurrentPhase(target, new Date('2026-11-09T14:59:59Z'))).toBe('listing-scheduled')
    expect(getCurrentPhase(target, new Date('2026-11-09T15:00:00Z'))).toBe('listed')
  })

  it('getPhaseLabel returns correct Korean labels', () => {
    expect(getPhaseLabel('scheduled')).toBe('미청약')
    expect(getPhaseLabel('subscribing')).toBe('청약 진행 중')
    expect(getPhaseLabel('listing-scheduled')).toBe('상장 예정')
    expect(getPhaseLabel('listing-unknown')).toBe('상장일 미정')
    expect(getPhaseLabel('listed')).toBe('상장 완료')
  })

  it('청약 내역이 모두 배정되고 전량 매도되면 sold로 집계한다', () => {
    const sold = {
      ...item(1, {
        id: 11,
        allocatedQuantity: 5,
        allocatedAmount: null,
        allocationStatus: 'CONFIRMED',
      }),
      ipoId: 1,
      sales: [
        {
          id: 21,
          quantity: 5,
          amount: null,
          fee: null,
          tax: null,
          otherCost: null,
          status: 'ACTIVE',
          soldAt: '2026-10-10',
        },
      ],
      remainingQuantity: 0,
    }

    expect(aggregateSubscriptionSchedule([ipo(1, '2026-10-01')], [sold])[0].status).toBe('sold')
  })
})
