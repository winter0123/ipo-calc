/**
 * label("상장 후 3개월 뒤", "2년 6개월", "15일" 등)의 기간을 모두 더해 상장일에 더한 예상 해제일(YYYY-MM-DD).
 * 기간을 못 읽으면 null.
 */
export function expectedUnlockDate(listingDate: string | null, label: string): string | null {
  const m = listingDate?.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  // '1.5년'의 5, '2026년'처럼 기간이 아닌 숫자는 버린다(소수점 뒤 숫자 제외, 년은 두 자리까지).
  const parts = [...label.matchAll(/(?<![\d.])(\d{1,3})\s*(개월|년|일)/g)].filter(
    ([, n, unit]) => unit !== '년' || Number(n) < 100,
  )
  if (!m || parts.length === 0 || /\d\.\d/.test(label)) return null
  let months = 0
  let days = 0
  for (const [, n, unit] of parts) {
    if (unit === '년') months += Number(n) * 12
    else if (unit === '개월') months += Number(n)
    else days += Number(n)
  }
  const total = Number(m[2]) - 1 + months
  const ty = Number(m[1]) + Math.floor(total / 12)
  const tm = total % 12
  // 대상 달에 그 일자가 없으면(예: 31일) 말일로 맞춘다.
  const last = new Date(Date.UTC(ty, tm + 1, 0)).getUTCDate()
  return new Date(Date.UTC(ty, tm, Math.min(Number(m[3]), last) + days)).toISOString().slice(0, 10)
}
