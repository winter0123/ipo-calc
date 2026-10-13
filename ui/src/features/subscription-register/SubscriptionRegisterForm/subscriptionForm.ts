import type { Ipo } from '@/entities/ipo/index.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { dateOnly } from '@/shared/lib/date.ts'

const DEPOSIT_RATE = 0.5

/** 'YYYY-MM-DD'(시각이 붙어도 됨)를 시간대 영향 없이 '2026. 10. 13.' 형식으로 바꾼다. */
export function formatDate(value: string): string {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ko-KR')
}

/** 증거금 계산에 쓰는 공모가: 확정가가 없으면 밴드 상단. 모르면 undefined. */
const basePrice = (ipo: Ipo) => ipo.fixedPrice ?? ipo.upperBandPrice ?? undefined

export function ipoPriceInfo(ipo: Ipo): string {
  if (ipo.fixedPrice != null) return `공모가 ${formatWon(ipo.fixedPrice)}`
  if (ipo.lowerBandPrice != null && ipo.upperBandPrice != null) {
    return `희망 공모가 ${formatWon(ipo.lowerBandPrice)} ~ ${formatWon(ipo.upperBandPrice)}`
  }
  return '공모가 미정'
}

/** 기본 증거금 = 공모가 × 청약수량 × 50%(원 단위 올림). 계산할 수 없으면 undefined. */
export function depositFor(ipo: Ipo | undefined, quantity: number): number | undefined {
  const price = ipo && basePrice(ipo)
  if (price === undefined || !Number.isInteger(quantity) || quantity <= 0) return undefined
  return Math.ceil(price * quantity * DEPOSIT_RATE)
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
