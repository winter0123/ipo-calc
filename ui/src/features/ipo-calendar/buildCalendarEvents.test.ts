import { describe, it, expect } from 'vitest'
import {
  buildCalendarEvents,
  eventCoversDate,
  buildWeekSegments,
  parseVisibleTypes,
  toTypesParam,
} from './buildCalendarEvents.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { CalendarEventType } from './buildCalendarEvents.ts'

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
  it('한 달 안의 청약은 SUBSCRIPTION 1개(startDate~endDate)로 생성한다', () => {
    const ipos = [makeIpo()]
    const events = buildCalendarEvents(ipos, 2026, 10)

    const subEvents = events.filter((e) => e.type === 'SUBSCRIPTION')
    expect(subEvents).toHaveLength(1)
    expect(subEvents[0].startDate).toBe('2026-10-01')
    expect(subEvents[0].endDate).toBe('2026-10-02')
    expect(subEvents[0].ipoId).toBe(1)
  })

  it('상장은 LISTING 1개(startDate===endDate)로 생성한다', () => {
    const ipos = [makeIpo()]
    const events = buildCalendarEvents(ipos, 2026, 10)

    const listingEvents = events.filter((e) => e.type === 'LISTING')
    expect(listingEvents).toHaveLength(1)
    expect(listingEvents[0].startDate).toBe('2026-10-15')
    expect(listingEvents[0].endDate).toBe('2026-10-15')
    expect(listingEvents[0].ipoId).toBe(1)
  })

  it('월을 넘기는 청약(예: 9/29~10/2)은 9월·10월 양쪽에서 나온다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-09-29T09:00:00Z',
        subscriptionEndDate: '2026-10-02T17:00:00Z',
      }),
    ]
    const sepEvents = buildCalendarEvents(ipos, 2026, 9)
    const octEvents = buildCalendarEvents(ipos, 2026, 10)

    const sepSub = sepEvents.find((e) => e.type === 'SUBSCRIPTION')
    const octSub = octEvents.find((e) => e.type === 'SUBSCRIPTION')

    expect(sepSub).toBeDefined()
    expect(sepSub?.startDate).toBe('2026-09-29')
    expect(sepSub?.endDate).toBe('2026-10-02')

    expect(octSub).toBeDefined()
    expect(octSub?.startDate).toBe('2026-09-29')
    expect(octSub?.endDate).toBe('2026-10-02')
  })

  it('상장일 null이면 LISTING 이벤트를 만들지 않는다', () => {
    const ipos = [makeIpo({ listingDate: null })]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events.find((e) => e.type === 'LISTING')).toBeUndefined()
    expect(events).toHaveLength(1)
    events.forEach((e) => expect(e.ipoId).toBe(1))
  })

  it('정렬: startDate 순, 같으면 청약이 상장보다 먼저, 같으면 ipoId 순', () => {
    const ipos = [
      makeIpo({
        id: 2,
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-01T17:00:00Z',
        listingDate: '2026-10-01T09:00:00Z',
      }),
      makeIpo({
        id: 1,
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-01T17:00:00Z',
        listingDate: '2026-10-01T09:00:00Z',
      }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events.map((e) => e.ipoId)).toEqual([1, 2, 1, 2])
    expect(events.map((e) => e.type)).toEqual([
      'SUBSCRIPTION',
      'SUBSCRIPTION',
      'LISTING',
      'LISTING',
    ])
  })

  it('종료일이 시작일보다 앞서는 비정상 데이터는 종료일=시작일로 본다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-05T09:00:00Z',
        subscriptionEndDate: '2026-10-01T17:00:00Z',
      }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    const sub = events.find((e) => e.type === 'SUBSCRIPTION')
    expect(sub).toBeDefined()
    expect(sub?.startDate).toBe('2026-10-05')
    expect(sub?.endDate).toBe('2026-10-05')
  })

  it('빈 배열을 받으면 빈 배열을 반환한다', () => {
    const events = buildCalendarEvents([], 2026, 10)
    expect(events).toEqual([])
  })

  it('여러 IPO가 있으면 각 이벤트에 올바른 ipoId가 들어간다', () => {
    const ipos = [
      makeIpo({ id: 1, subscriptionStartDate: '2026-10-01T09:00:00Z' }),
      makeIpo({ id: 2, subscriptionStartDate: '2026-10-02T09:00:00Z' }),
    ]
    const events = buildCalendarEvents(ipos, 2026, 10)

    expect(events).toHaveLength(4)
    const ipo1Events = events.filter((e) => e.ipoId === 1)
    expect(ipo1Events).toHaveLength(2)
    const ipo2Events = events.filter((e) => e.ipoId === 2)
    expect(ipo2Events).toHaveLength(2)
  })
})

describe('eventCoversDate', () => {
  const event = {
    ipoId: 1,
    type: 'SUBSCRIPTION' as const,
    companyName: '테스트',
    ipoName: '테스트',
    startDate: '2026-10-01',
    endDate: '2026-10-03',
  }

  it('시작일~종료일 사이의 날짜는 true를 반환한다', () => {
    expect(eventCoversDate(event, '2026-10-01')).toBe(true)
    expect(eventCoversDate(event, '2026-10-02')).toBe(true)
    expect(eventCoversDate(event, '2026-10-03')).toBe(true)
  })

  it('범위 밖 날짜는 false를 반환한다', () => {
    expect(eventCoversDate(event, '2026-09-30')).toBe(false)
    expect(eventCoversDate(event, '2026-10-04')).toBe(false)
  })

  it('상장 이벤트(startDate===endDate)도 정확히 판정한다', () => {
    const listingEvent = {
      ...event,
      type: 'LISTING' as const,
      startDate: '2026-10-15',
      endDate: '2026-10-15',
    }
    expect(eventCoversDate(listingEvent, '2026-10-15')).toBe(true)
    expect(eventCoversDate(listingEvent, '2026-10-14')).toBe(false)
    expect(eventCoversDate(listingEvent, '2026-10-16')).toBe(false)
  })
})

describe('buildWeekSegments', () => {
  const weekDates = [
    '2026-10-04',
    '2026-10-05',
    '2026-10-06',
    '2026-10-07',
    '2026-10-08',
    '2026-10-09',
    '2026-10-10',
  ]

  it('한 주 안 2일 청약: startCol/endCol 올바름, continues 둘 다 false', () => {
    const events = [
      {
        ipoId: 1,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트',
        ipoName: '테스트',
        startDate: '2026-10-05',
        endDate: '2026-10-06',
      },
    ]
    const { segments, laneCount } = buildWeekSegments(events, weekDates)

    expect(segments).toHaveLength(1)
    expect(segments[0].startCol).toBe(1)
    expect(segments[0].endCol).toBe(2)
    expect(segments[0].continuesBefore).toBe(false)
    expect(segments[0].continuesAfter).toBe(false)
    expect(segments[0].lane).toBe(0)
    expect(laneCount).toBe(1)
  })

  it('주를 넘기는 청약: 두 주에서 각각 continuesAfter / continuesBefore true', () => {
    const events = [
      {
        ipoId: 1,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트',
        ipoName: '테스트',
        startDate: '2026-10-08',
        endDate: '2026-10-12',
      },
    ]

    const week1 = [
      '2026-10-04',
      '2026-10-05',
      '2026-10-06',
      '2026-10-07',
      '2026-10-08',
      '2026-10-09',
      '2026-10-10',
    ]
    const week2 = [
      '2026-10-11',
      '2026-10-12',
      '2026-10-13',
      '2026-10-14',
      '2026-10-15',
      '2026-10-16',
      '2026-10-17',
    ]

    const { segments: seg1 } = buildWeekSegments(events, week1)
    const { segments: seg2 } = buildWeekSegments(events, week2)

    expect(seg1[0].continuesAfter).toBe(true)
    expect(seg1[0].continuesBefore).toBe(false)
    expect(seg1[0].endCol).toBe(6)

    expect(seg2[0].continuesBefore).toBe(true)
    expect(seg2[0].continuesAfter).toBe(false)
    expect(seg2[0].startCol).toBe(0)
    expect(seg2[0].endCol).toBe(1)
  })

  it('겹치는 두 청약은 lane 0, 1에 배치된다', () => {
    const events = [
      {
        ipoId: 1,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트1',
        ipoName: '테스트1',
        startDate: '2026-10-05',
        endDate: '2026-10-07',
      },
      {
        ipoId: 2,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트2',
        ipoName: '테스트2',
        startDate: '2026-10-06',
        endDate: '2026-10-08',
      },
    ]
    const { segments, laneCount } = buildWeekSegments(events, weekDates)

    expect(laneCount).toBe(2)
    const lanes = segments.map((s) => s.lane).sort()
    expect(lanes).toEqual([0, 1])
  })

  it('안 겹치면 둘 다 lane 0에 배치된다', () => {
    const events = [
      {
        ipoId: 1,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트1',
        ipoName: '테스트1',
        startDate: '2026-10-04',
        endDate: '2026-10-05',
      },
      {
        ipoId: 2,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트2',
        ipoName: '테스트2',
        startDate: '2026-10-07',
        endDate: '2026-10-08',
      },
    ]
    const { segments, laneCount } = buildWeekSegments(events, weekDates)

    expect(laneCount).toBe(1)
    expect(segments.every((s) => s.lane === 0)).toBe(true)
  })

  it('빈 칸(null)이 낀 주(달의 첫째·마지막 주)에서 열 계산이 올바르다', () => {
    const weekWithNulls = [
      null,
      null,
      '2026-10-01',
      '2026-10-02',
      '2026-10-03',
      '2026-10-04',
      '2026-10-05',
    ]
    const events = [
      {
        ipoId: 1,
        type: 'SUBSCRIPTION' as const,
        companyName: '테스트',
        ipoName: '테스트',
        startDate: '2026-10-01',
        endDate: '2026-10-03',
      },
    ]
    const { segments } = buildWeekSegments(events, weekWithNulls)

    expect(segments[0].startCol).toBe(2)
    expect(segments[0].endCol).toBe(4)
  })

  it('막대 없음: laneCount 0', () => {
    const { segments, laneCount } = buildWeekSegments([], weekDates)
    expect(segments).toHaveLength(0)
    expect(laneCount).toBe(0)
  })

  it('청약과 상장이 같은 날 겹치면 청약이 먼저 배치된다(정렬)', () => {
    const events = [
      {
        ipoId: 1,
        type: 'LISTING' as const,
        companyName: '상장',
        ipoName: '상장',
        startDate: '2026-10-05',
        endDate: '2026-10-05',
      },
      {
        ipoId: 2,
        type: 'SUBSCRIPTION' as const,
        companyName: '청약',
        ipoName: '청약',
        startDate: '2026-10-05',
        endDate: '2026-10-05',
      },
    ]
    const { segments } = buildWeekSegments(events, weekDates)

    expect(segments[0].event.type).toBe('SUBSCRIPTION')
    expect(segments[1].event.type).toBe('LISTING')
  })
})

describe('parseVisibleTypes', () => {
  it('쿼리 없음: 2종 모두 켜짐', () => {
    const params = new URLSearchParams()
    const visible = parseVisibleTypes(params)
    expect(visible.has('SUBSCRIPTION')).toBe(true)
    expect(visible.has('LISTING')).toBe(true)
    expect(visible.size).toBe(2)
  })

  it('일부만 지정: 지정된 것만 켜짐', () => {
    const params = new URLSearchParams('types=SUBSCRIPTION')
    const visible = parseVisibleTypes(params)
    expect(visible.has('SUBSCRIPTION')).toBe(true)
    expect(visible.has('LISTING')).toBe(false)
    expect(visible.size).toBe(1)
  })

  it('알 수 없는 값은 무시', () => {
    const params = new URLSearchParams('types=SUBSCRIPTION,UNKNOWN,LISTING')
    const visible = parseVisibleTypes(params)
    expect(visible.has('SUBSCRIPTION')).toBe(true)
    expect(visible.has('LISTING')).toBe(true)
    expect(visible.size).toBe(2)
  })

  it('알 수 있는 값이 하나도 없으면(types=foo): 2종 모두 켜짐', () => {
    const params = new URLSearchParams('types=foo')
    const visible = parseVisibleTypes(params)
    expect(visible.has('SUBSCRIPTION')).toBe(true)
    expect(visible.has('LISTING')).toBe(true)
    expect(visible.size).toBe(2)
  })

  it('빈 값(types=): 0종(모두 꺼짐)', () => {
    const params = new URLSearchParams('types=')
    const visible = parseVisibleTypes(params)
    expect(visible.size).toBe(0)
  })

  it('예전 값(SUBSCRIPTION_START 등)은 알 수 없는 값으로 처리되어 모두 켜짐', () => {
    const params = new URLSearchParams('types=SUBSCRIPTION_START,SUBSCRIPTION_END')
    const visible = parseVisibleTypes(params)
    expect(visible.has('SUBSCRIPTION')).toBe(true)
    expect(visible.has('LISTING')).toBe(true)
    expect(visible.size).toBe(2)
  })
})

describe('toTypesParam', () => {
  it('2종 모두 켜짐: null(쿼리에서 types 제거)', () => {
    const visible = new Set<CalendarEventType>(['SUBSCRIPTION', 'LISTING'])
    expect(toTypesParam(visible)).toBeNull()
  })

  it('1종만 켜짐: 해당 값만', () => {
    const visible = new Set<CalendarEventType>(['LISTING'])
    expect(toTypesParam(visible)).toBe('LISTING')
  })

  it('0종(모두 꺼짐): 빈 문자열(types=)이며 모두 켜짐(null)과 구분된다', () => {
    const visible = new Set<CalendarEventType>()
    expect(toTypesParam(visible)).toBe('')
  })
})
