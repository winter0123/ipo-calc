import { describe, expect, it } from 'vitest'
import { profitTone, profitToneClass } from './profitTone.ts'

describe('profitTone', () => {
  it('이익은 gain, 손실은 loss, 0은 flat', () => {
    expect(profitTone(1)).toBe('gain')
    expect(profitTone(-1)).toBe('loss')
    expect(profitTone(0)).toBe('flat')
    expect(profitTone(-0)).toBe('flat')
  })
  it('클래스 이름', () => {
    expect(profitToneClass(5)).toBe('profit-gain')
    expect(profitToneClass(-5)).toBe('profit-loss')
    expect(profitToneClass(0)).toBe('')
  })
})
