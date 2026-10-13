import { describe, expect, it } from 'vitest'
import { toLocalYmd } from './date.ts'

describe('toLocalYmd', () => {
  it('로컬 날짜를 0을 채운 YYYY-MM-DD로 만든다', () => {
    expect(toLocalYmd(new Date(2026, 0, 5, 8, 30))).toBe('2026-01-05')
    expect(toLocalYmd(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31')
  })
})
