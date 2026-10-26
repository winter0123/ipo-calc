import { describe, expect, it } from 'vitest'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import {
  aggregateSubscriptionSchedule,
  currentMonthRange,
  filterIposBySubscriptionStart,
  parseDateRange,
  updateDateRangeParams,
} from './subscriptionSchedule.ts'

const ipo = (id: number, start: string): Ipo => ({
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
            allocationPrice: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 3,
        },
        {
          ...item(4, {
            id: 4,
            allocatedQuantity: 3,
            allocationPrice: null,
            allocationStatus: 'CONFIRMED',
          }),
          ipoId: 4,
        },
        {
          ...item(5, {
            id: 5,
            allocatedQuantity: 0,
            allocationPrice: null,
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
            allocationPrice: null,
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
})
