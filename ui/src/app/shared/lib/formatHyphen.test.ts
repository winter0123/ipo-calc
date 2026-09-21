import { describe, it, expect } from 'vitest'
import { formatHyphen } from './formatHyphen.ts'

describe('formatHyphen', () => {
  it('4자리마다 하이픈을 삽입한다', () => {
    expect(formatHyphen('0000000000000000')).toBe('0000-0000-0000-0000')
  })

  it('빈 문자열을 반환한다', () => {
    expect(formatHyphen('')).toBe('')
  })

  it('하이픈이 없는 짧은 문자열을 반환한다', () => {
    expect(formatHyphen('123')).toBe('123')
  })
})
