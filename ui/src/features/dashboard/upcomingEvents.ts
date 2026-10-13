import type { Ipo } from '@/entities/ipo/index.ts'
import { dateOnly, toLocalYmd } from '@/shared/lib/date.ts'

export type UpcomingKind = '청약 시작' | '청약 마감' | '환불' | '상장'

export interface UpcomingEvent {
  key: string
  ipoId: number
  ipoName: string
  kind: UpcomingKind
  date: string
  /** 청약 기간(시작~마감) 중이라 지금 청약 등록을 할 수 있는 공모주 */
  canSubscribe: boolean
}

/** 오늘부터 7일 안의 청약 시작·마감·환불·상장 일정을 날짜순으로 최대 limit건. */
export function buildUpcomingEvents(ipos: Ipo[], today: Date, limit = 5): UpcomingEvent[] {
  const from = toLocalYmd(today)
  const to = toLocalYmd(new Date(today.getFullYear(), today.getMonth(), today.getDate() + 7))
  const order: Record<UpcomingKind, number> = { '청약 시작': 0, '청약 마감': 1, 환불: 2, 상장: 3 }
  const events: UpcomingEvent[] = []

  for (const ipo of ipos) {
    const start = dateOnly(ipo.subscriptionStartDate)
    const end = dateOnly(ipo.subscriptionEndDate)
    const canSubscribe = start <= from && from <= end
    const add = (kind: UpcomingKind, date: string | null | undefined) => {
      if (!date) return
      const d = dateOnly(date)
      if (d < from || d > to) return
      events.push({
        key: `${ipo.id}-${kind}`,
        ipoId: ipo.id,
        ipoName: ipo.ipoName,
        kind,
        date: d,
        canSubscribe: canSubscribe && (kind === '청약 시작' || kind === '청약 마감'),
      })
    }
    add('청약 시작', start)
    add('청약 마감', end)
    add('환불', ipo.refundDate)
    add('상장', ipo.listingDate)
  }

  events.sort(
    (a, b) => a.date.localeCompare(b.date) || order[a.kind] - order[b.kind] || a.ipoId - b.ipoId,
  )
  return events.slice(0, limit)
}
