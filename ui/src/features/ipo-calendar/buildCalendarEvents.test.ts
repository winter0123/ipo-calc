import { describe, it, expect } from 'vitest'
import { buildCalendarEvents } from './buildCalendarEvents.ts'
import type { Ipo } from '@/entities/ipo/types.ts'

function makeIpo(overrides: Partial<Ipo> = {}): Ipo {
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
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}

describe('buildCalendarEvents', () => {
  it('3종 이벤트(SUBSCRIPTION_START, SUBSCRIPTION_END, LISTING)를 모두 생성한다', () => {
    const ipos = [makeIpo()]
    const events = buildCalendarEvents(ipos, 2026, 10)

    const types = events.map((e) => e.type).sort()
    expect(types).toEqual(['LISTING', 'SUBSCRIPTION_END', 'SUBSCRIPTION_START'])
  })

  it('시작일과 마감일이 같은 날이면 두 이벤트를 모두 생성한다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-05T09:00:00Z',
        subscriptionEndDate: '2026-10-05T17:00:00Z',
      }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    const dayEvents = events.filter((e) => e.date === '2026-10-05')
    expect(dayEvents).toHaveLength(2)
    expect(dayEvents.map((e) => e.type).sort()).toEqual(['SUBSCRIPTION_END', 'SUBSCRIPTION_START'])
  })

  it('listingDate가 null이면 상장 이벤트를 만들지 않는다', () => {
    const ipos = [makeIpo({ listingDate: null })]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events.find((e) => e.type === 'LISTING')).toBeUndefined()
    expect(events).toHaveLength(2)
  })

  it('표시할 월에 속하지 않는 날짜는 제외한다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-09-30T09:00:00Z',
        subscriptionEndDate: '2026-10-01T17:00:00Z',
        listingDate: '2026-11-01T09:00:00Z',
      }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events).toHaveLength(1)
    expect(events[0].date).toBe('2026-10-01')
    expect(events[0].type).toBe('SUBSCRIPTION_END')
  })

  it('2026-10-01이 10월 1일로 남는지 확인(시간대 밀림 없음)', () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events[0].date).toBe('2026-10-01')
  })

  it('월 경계(10/31, 11/1)를 정확히 처리한다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-31T09:00:00Z',
        subscriptionEndDate: '2026-11-01T17:00:00Z',
        listingDate: '2026-10-31T09:00:00Z',
      }),
    ]

    const octEvents = buildCalendarEvents(ipos, 2026, 10)
    const novEvents = buildCalendarEvents(ipos, 2026, 11)

    expect(octEvents.map((e) => e.date).sort()).toEqual(['2026-10-31', '2026-10-31'])
    expect(novEvents.map((e) => e.date)).toEqual(['2026-11-01'])
  })

  it('고정가가 있으면 고정가를, 없으면 밴드 가격을 표시한다', () => {
    const iposFixed = [makeIpo({ fixedPrice: 20000, lowerBandPrice: 15000, upperBandPrice: 25000 })]
    const iposBand = [makeIpo({ fixedPrice: 0, lowerBandPrice: 15000, upperBandPrice: 25000 })]

    const eventsFixed = buildCalendarEvents(iposFixed, 2026, 10)
    const eventsBand = buildCalendarEvents(iposBand, 2026, 10)

    expect(eventsFixed[0].price).toBe('20,000원')
    expect(eventsBand[0].price).toBe('15,000~25,000원')
  })

  it('가격 컬럼이 모두 null이면 가격을 표시하지 않는다(DB nullable)', () => {
    const ipos = [makeIpo({ fixedPrice: null, lowerBandPrice: null, upperBandPrice: null })]

    expect(buildCalendarEvents(ipos, 2026, 10)[0].price).toBeUndefined()
  })

  it('이벤트가 날짜순으로 정렬된다', () => {
    const ipos = [
      makeIpo({
        id: 1,
        subscriptionStartDate: '2026-10-05T09:00:00Z',
        subscriptionEndDate: '2026-10-05T17:00:00Z',
        listingDate: '2026-10-01T09:00:00Z',
      }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events.map((e) => e.date)).toEqual(['2026-10-01', '2026-10-05', '2026-10-05'])
  })

  it('빈 배열을 받으면 빈 배열을 반환한다', () => {
    const events = buildCalendarEvents([], 2026, 10)
    expect(events).toEqual([])
  })
})
