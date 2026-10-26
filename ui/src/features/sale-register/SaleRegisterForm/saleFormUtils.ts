export const won = (value: number) => `${value.toLocaleString('ko-KR')}원`

export const isNum = (value: unknown): value is number =>
  typeof value === 'number' && !Number.isNaN(value)
