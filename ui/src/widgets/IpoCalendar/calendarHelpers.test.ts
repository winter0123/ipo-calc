import { buildWeekRows } from './calendarHelpers.ts'

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
