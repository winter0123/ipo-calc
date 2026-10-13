import { describe, expect, it } from 'vitest'
import { dateOnly, toLocalYmd } from './date.ts'

describe('toLocalYmd', () => {
  it('로컬 날짜를 0을 채운 YYYY-MM-DD로 만든다', () => {
    expect(toLocalYmd(new Date(2026, 0, 5, 8, 30))).toBe('2026-01-05')
    expect(toLocalYmd(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31')
  })
})

describe('dateOnly', () => {
  it('시각을 잘라 날짜만 남기고 null은 null', () => {
    expect(dateOnly('2026-10-15T09:00:00')).toBe('2026-10-15')
    expect(dateOnly('2026-10-15')).toBe('2026-10-15')
    expect(dateOnly(null)).toBeNull()
  })
})
