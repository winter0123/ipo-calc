import type { Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'

export interface DateRange {
  from: string
  to: string
}

/** 날짜 범위 입력 하나를 URL에 반영하고, 범위 순서와 나머지 쿼리 값을 보존한다. */
export function updateDateRangeParams(
  params: URLSearchParams,
  key: 'from' | 'to',
  value: string,
  fallback: DateRange,
): URLSearchParams {
  const next = new URLSearchParams(params)
  next.set(key, value)
  const otherKey = key === 'from' ? 'to' : 'from'
  if (!next.has(otherKey)) next.set(otherKey, fallback[otherKey])
  const otherValue = next.get(otherKey) ?? fallback[otherKey]
  if ((key === 'from' && value > otherValue) || (key === 'to' && value < otherValue)) {
    next.set(otherKey, value)
  }
  return next
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
  const validDate = (value: string | null) => {
    if (!value) return false
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
    if (!match) return false
    const year = Number(match[1])
    const month = Number(match[2])
    const day = Number(match[3])
    if (month < 1 || month > 12) return false
    const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
    const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    return day >= 1 && day <= daysInMonth[month - 1]
  }
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
    const subscriptions = historyByIpo.get(item.ipoId)
    if (subscriptions) subscriptions.push(item)
    else historyByIpo.set(item.ipoId, [item])
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
