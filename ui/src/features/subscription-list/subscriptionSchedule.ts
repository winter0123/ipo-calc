import type { Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'

export interface DateRange {
  from: string
  to: string
}

export interface SubscriptionScheduleItem {
  ipo: Ipo
  subscriptions: InvestmentHistoryItem[]
  subscriptionCount: number
  subscriptionQuantity: number
  allocatedCount: number
  allocatedQuantity: number
  pendingCount: number
  status: 'before' | 'pending' | 'partial' | 'allocated' | 'unallocated'
}

export function currentMonthRange(now = new Date()): DateRange {
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  const last = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  return { from: toLocalYmd(first), to: toLocalYmd(last) }
}

export function parseDateRange(from: string | null, to: string | null): DateRange {
  const fallback = currentMonthRange()
  const validDate = (value: string | null) => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value)
  if (!validDate(from) || !validDate(to) || from! > to!) return fallback
  return { from: from!, to: to! }
}

export function filterIposBySubscriptionStart(ipos: Ipo[], range: DateRange): Ipo[] {
  return ipos
    .filter((ipo) => {
      const start = ipo.subscriptionStartDate.slice(0, 10)
      return start >= range.from && start <= range.to
    })
    .sort((a, b) =>
      a.subscriptionStartDate.slice(0, 10).localeCompare(b.subscriptionStartDate.slice(0, 10)),
    )
}

export function aggregateSubscriptionSchedule(
  ipos: Ipo[],
  history: InvestmentHistoryItem[],
): SubscriptionScheduleItem[] {
  const historyByIpo = new Map<number, InvestmentHistoryItem[]>()
  for (const item of history) {
    historyByIpo.set(item.ipoId, [...(historyByIpo.get(item.ipoId) ?? []), item])
  }
  return ipos.map((ipo) => {
    const subscriptions = historyByIpo.get(ipo.id) ?? []
    const allocated = subscriptions.filter((item) => item.allocation !== null)
    const allocatedQuantity = allocated.reduce(
      (sum, item) => sum + (item.allocation?.allocatedQuantity ?? 0),
      0,
    )
    const pendingCount = subscriptions.length - allocated.length
    let status: SubscriptionScheduleItem['status'] = 'before'
    if (subscriptions.length > 0) {
      if (pendingCount === subscriptions.length) status = 'pending'
      else if (pendingCount > 0) status = 'partial'
      else status = allocatedQuantity > 0 ? 'allocated' : 'unallocated'
    }
    return {
      ipo,
      subscriptions,
      subscriptionCount: subscriptions.length,
      subscriptionQuantity: subscriptions.reduce((sum, item) => sum + item.subscriptionQuantity, 0),
      allocatedCount: allocated.length,
      allocatedQuantity,
      pendingCount,
      status,
    }
  })
}
