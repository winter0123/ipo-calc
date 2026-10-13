import { describe, it, expect } from 'vitest'
import { expectedUnlockDate } from './lockUpDate.ts'

describe('expectedUnlockDate', () => {
  it('개월·년·일 기간을 상장일에 더한다', () => {
    expect(expectedUnlockDate('2026-10-22', '상장 후 3개월 뒤')).toBe('2027-01-22')
    expect(expectedUnlockDate('2026-10-22', '1년')).toBe('2027-10-22')
    expect(expectedUnlockDate('2026-10-22', '상장 후 15일')).toBe('2026-11-06')
  })
  it('여러 기간이 섞인 label은 모두 더한다', () => {
    expect(expectedUnlockDate('2026-10-22', '상장 후 2년 6개월 뒤 유통가능')).toBe('2029-04-22')
  })
  it('말일을 넘기는 달은 말일로 맞춘다', () => {
    expect(expectedUnlockDate('2026-08-31', '6개월')).toBe('2027-02-28')
  })
  it('소수 기간이나 연도 숫자는 기간으로 읽지 않는다', () => {
    expect(expectedUnlockDate('2026-10-14', '상장 후 1.5년 뒤')).toBeNull()
    expect(expectedUnlockDate('2026-10-14', '2026년 3월 신청, 3개월')).toBe('2027-01-14')
  })
  it('기간을 못 읽거나 상장일이 없으면 null', () => {
    expect(expectedUnlockDate('2026-10-22', '상장일 유통가능')).toBeNull()
    expect(expectedUnlockDate(null, '3개월')).toBeNull()
  })
})
