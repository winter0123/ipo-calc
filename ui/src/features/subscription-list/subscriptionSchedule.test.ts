import { describe, expect, it } from 'vitest'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import {
  aggregateSubscriptionSchedule,
  currentMonthRange,
  filterIposBySubscriptionStart,
  parseDateRange,
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
})
