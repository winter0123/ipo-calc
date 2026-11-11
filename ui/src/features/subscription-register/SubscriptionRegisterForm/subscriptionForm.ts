import type { Ipo } from '@/entities/ipo/index.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { dateOnly } from '@/shared/lib/date.ts'

const DEPOSIT_RATE = 0.5

/** 'YYYY-MM-DD'(시각이 붙어도 됨)를 시간대 영향 없이 '2026. 10. 13.' 형식으로 바꾼다. */
export function formatDate(value: string): string {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ko-KR')
}

/** 금액 계산에 쓰는 공모가는 확정 공모가뿐이다. 희망가 밴드는 쓰지 않는다. */
const basePrice = (ipo: Ipo) => ipo.fixedPrice ?? undefined

export function ipoPriceInfo(ipo: Ipo): string {
  if (ipo.fixedPrice != null) return `공모가 ${formatWon(ipo.fixedPrice)}`
  return '공모가 미정'
}

/** 한국 시간대 오늘을 'YYYY-MM-DD'로. 브라우저 시간대와 무관하게 한국 기준 날짜를 쓴다. */
export function koreanToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(now)
}

const ymd = /^\d{4}-\d{2}-\d{2}$/

/** '2026-11-10T00:00:00' → '2026-11-10'. null/undefined/형식 오류면 null. */
function periodDay(value: string | null | undefined): string | null {
  const day = value?.slice(0, 10) ?? ''
  return ymd.test(day) ? day : null
}

/**
 * 청약 기간의 실제 날짜 목록(첫날~마지막날, 포함). 시작·종료가 없거나 형식이 잘못됐거나
 * 시작이 종료보다 뒤면 null을 돌려준다(임의 값으로 대체하지 않는다).
 */
export function subscriptionPeriodDates(ipo: {
  subscriptionStartDate: string | null | undefined
  subscriptionEndDate: string | null | undefined
}): string[] | null {
  const start = periodDay(ipo.subscriptionStartDate)
  const end = periodDay(ipo.subscriptionEndDate)
  if (!start || !end || start > end) return null
  const dates: string[] = []
  const cursor = new Date(`${start}T00:00:00Z`)
  const last = new Date(`${end}T00:00:00Z`)
  while (cursor <= last) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return dates.length > 0 ? dates : null
}

/** 기본 청약일: 한국 오늘이 기간 안이면 오늘, 아니면 첫날. dates가 비면 빈 문자열. */
export function defaultSubscribedAt(dates: string[], today: string): string {
  if (dates.length === 0) return ''
  return dates.includes(today) ? today : dates[0]
}

/** 기본 증거금 = 확정 공모가 × 청약수량 × 50%(원 단위 올림). 계산할 수 없으면 undefined. */
export function depositFor(ipo: Ipo | undefined, quantity: number): number | undefined {
  const price = ipo && basePrice(ipo)
  if (price === undefined || !Number.isInteger(quantity) || quantity <= 0) return undefined
  return Math.ceil(price * quantity * DEPOSIT_RATE)
}

/** 배정 공모주 총 가격 = 확정 공모가 × 배정수량. 확정가가 없으면 undefined. */
export function allocationTotalFor(
  ipo: Ipo | undefined,
  allocatedQuantity: number,
): number | undefined {
  const price = ipo && basePrice(ipo)
  if (price === undefined) return undefined
  const quantity =
    Number.isFinite(allocatedQuantity) && allocatedQuantity > 0 ? allocatedQuantity : 0
  return price * quantity
}

/** 청약 진행 중 → 예정(빠른 순) → 마감(최근 순) 순서. today는 'YYYY-MM-DD'. */
export function sortIposForSubscription(ipos: Ipo[], today: string): Ipo[] {
  const rank = (ipo: Ipo) =>
    dateOnly(ipo.subscriptionEndDate) < today
      ? 2
      : dateOnly(ipo.subscriptionStartDate) > today
        ? 1
        : 0
  return [...ipos].sort((a, b) => {
    const diff = rank(a) - rank(b)
    if (diff !== 0) return diff
    const byStart = a.subscriptionStartDate.localeCompare(b.subscriptionStartDate)
    return rank(a) === 2 ? -byStart : byStart
  })
}
