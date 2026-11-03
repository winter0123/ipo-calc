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
