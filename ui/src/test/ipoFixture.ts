import type { Ipo } from '@/entities/ipo/index.ts'

export function makeIpo(overrides: Partial<Ipo> = {}): Ipo {
  return {
    id: 1,
    companyName: '테스트기업',
    ipoName: '테스트IPO',
    stockCode: '123456',
    roundLabel: '1',
    lowerBandPrice: 10000,
    upperBandPrice: 15000,
    fixedPrice: 0,
    subscriptionStartDate: '2026-10-01T09:00:00Z',
    subscriptionEndDate: '2026-10-02T17:00:00Z',
    listingDate: '2026-10-15T09:00:00Z',
    refundDate: null,
    refundDateEstimated: null,
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}
