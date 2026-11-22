import { barLabel, buildWeekRows } from './calendarHelpers.ts'
import type { CalendarEvent } from '@/features/ipo-calendar/index.ts'

describe('barLabel', () => {
  const listing = (overrides: Partial<CalendarEvent> = {}): CalendarEvent => ({
    ipoId: 1,
    type: 'LISTING',
    companyName: '테스트기업',
    ipoName: '테스트IPO',
    startDate: '2026-10-15',
    endDate: '2026-10-15',
    ...overrides,
  })

  it('상장 이벤트의 접근성 라벨에 화면에 보이는 증거 상태를 포함한다', () => {
    expect(barLabel(listing({ listingLabel: '상장 예정' }))).toBe(
      '테스트기업 상장 10월 15일 (상장 예정)',
    )
    expect(barLabel(listing({ listingLabel: '상장예정일 경과 · 거래소 미확인' }))).toBe(
      '테스트기업 상장 10월 15일 (상장예정일 경과 · 거래소 미확인)',
    )
    expect(barLabel(listing({ listingLabel: '상장 확정' }))).toBe(
      '테스트기업 상장 10월 15일 (상장 확정)',
    )
  })

  it('출처가 없는 기존 상장 이벤트 라벨은 기존과 같다', () => {
    expect(barLabel(listing())).toBe('테스트기업 상장 10월 15일')
  })
})

describe('buildWeekRows mobile (isMobile)', () => {
  // 2026-10 시작 요일: 목요일(4) — 첫 주 Mon~Fri 확인
  it('2026-10 첫 주 모바일: 월~금 5열, 3빈 + 1,2', () => {
    const rows = buildWeekRows(4, 31, true)
    expect(rows[0]).toEqual([null, null, null, 1, 2])
  })

  it('2026-10 두 번째 주 모바일: 5,6,7,8,9', () => {
    const rows = buildWeekRows(4, 31, true)
    expect(rows[1]).toEqual([5, 6, 7, 8, 9])
  })

  it('2026-10 마지막 주 모바일: 26~30 (31은 토요일 제거)', () => {
    const rows = buildWeekRows(4, 31, true)
    const last = rows[rows.length - 1]
    expect(last).toEqual([26, 27, 28, 29, 30])
  })
})
