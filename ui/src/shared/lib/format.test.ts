import { describe, expect, it } from 'vitest'
import { formatShares, formatWon } from './format.ts'

describe('format', () => {
  it('formatWon은 쉼표와 원 단위를 붙인다', () => {
    expect(formatWon(1234567)).toBe('1,234,567원')
    expect(formatWon(0)).toBe('0원')
    expect(formatWon(-1500)).toBe('-1,500원')
  })
  it('formatShares는 쉼표와 주 단위를 붙인다', () => {
    expect(formatShares(12000)).toBe('12,000주')
  })
})
