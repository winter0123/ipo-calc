export type ProfitTone = 'gain' | 'loss' | 'flat'

/** 국내 증권사 관례: 이익(+) 빨강, 손실(−) 파랑, 0은 기본 글자색. */
export function profitTone(value: number): ProfitTone {
  return value > 0 ? 'gain' : value < 0 ? 'loss' : 'flat'
}

export function profitToneClass(value: number): string {
  const tone = profitTone(value)
  return tone === 'flat' ? '' : `profit-${tone}`
}

/** recharts 등 CSS 클래스를 못 쓰는 곳용 색 변수. */
export const PROFIT_TONE_VAR = {
  gain: 'var(--gain)',
  loss: 'var(--loss)',
  flat: 'var(--ink)',
} as const
