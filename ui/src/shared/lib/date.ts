const pad = (n: number) => String(n).padStart(2, '0')

/** 로컬(한국) 날짜를 `YYYY-MM-DD`로. toISOString은 UTC라 오전 9시 전에 하루 전 날짜가 되므로 쓰지 않는다. */
export function toLocalYmd(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
