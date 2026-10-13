export const isNum = (value: unknown): value is number =>
  typeof value === 'number' && !Number.isNaN(value)
export const ymd = (value: string) => value.slice(0, 10)
/** 등록 화면과 같이 날짜만 받아 T09:00을 붙여 보낸다 */
export const at9 = (date: string) => `${date}T09:00`
