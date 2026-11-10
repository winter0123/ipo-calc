import { describe, expect, it } from 'vitest'
import { formatSecuritiesFirmName } from './formatSecuritiesFirmName.ts'

describe('formatSecuritiesFirmName', () => {
  it.each(['IBK투자증권', '아이비케이투자증권 주식회사', '아이비케이투자증권㈜'])(
    '%s를 IBK로 표시한다',
    (name) => {
      expect(formatSecuritiesFirmName(name)).toBe('IBK')
    },
  )

  it('다른 증권사명은 그대로 표시한다', () => {
    expect(formatSecuritiesFirmName('한국투자증권')).toBe('한국투자증권')
  })
})
