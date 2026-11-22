const pad = (n: number) => String(n).padStart(2, '0')
const SEOUL_YMD = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' })

/** 로컬(한국) 날짜를 `YYYY-MM-DD`로. toISOString은 UTC라 오전 9시 전에 하루 전 날짜가 되므로 쓰지 않는다. */
export function toLocalYmd(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 기기 시간대와 무관하게 한국 시간(Asia/Seoul) 기준 `YYYY-MM-DD`를 돌려준다. */
export function toSeoulYmd(date: Date = new Date()): string {
  return SEOUL_YMD.format(date)
}

/** `'2026-10-15T09:00:00'` → `'2026-10-15'`. null/undefined면 null. */
export function dateOnly(value: string): string
export function dateOnly(value: string | null | undefined): string | null
export function dateOnly(value: string | null | undefined): string | null {
  return value ? value.slice(0, 10) : null
}
