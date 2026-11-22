import { getListingDisplay, type Ipo } from '@/entities/ipo/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { toLocalYmd, toSeoulYmd } from '@/shared/lib/date.ts'

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
  status: 'before' | 'pending' | 'partial' | 'allocated' | 'unallocated' | 'sold'
}

export const PAGE_SIZE = 10

export function paginateItems<T>(items: T[], page: number): T[] {
  const start = page * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

export function getTotalPages(items: unknown[]): number {
  return Math.ceil(items.length / PAGE_SIZE)
}

export function clampPage(page: number, totalPages: number): number {
  if (totalPages === 0) return 0
  return Math.max(0, Math.min(page, totalPages - 1))
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
      const allAllocatedAndSold = subscriptions.every(
        (item) =>
          item.allocation !== null &&
          item.allocation.allocatedQuantity > 0 &&
          item.sales.length > 0 &&
          item.remainingQuantity === 0,
      )
      if (allAllocatedAndSold) status = 'sold'
      else if (pendingCount === subscriptions.length) status = 'pending'
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

export type IpoPhase =
  | 'scheduled'
  | 'subscribing'
  | 'listing-scheduled'
  | 'listing-overdue'
  | 'listing-confirmed'
  | 'listing-unknown'
  | 'listed'

export function getCurrentPhase(ipo: Ipo, today = new Date()): IpoPhase {
  const todayStr = toSeoulYmd(today)
  const subscriptionStart = ipo.subscriptionStartDate.slice(0, 10)
  const subscriptionEnd = ipo.subscriptionEndDate.slice(0, 10)
  const listingDate = ipo.listingDate ? ipo.listingDate.slice(0, 10) : null

  // 1. 청약 예정: subscriptionStart보다 이르면 listingDate 유무와 무관하게 scheduled
  if (todayStr < subscriptionStart) return 'scheduled'

  // 2. 청약 진행 중: subscriptionStart <= today <= subscriptionEnd
  if (todayStr >= subscriptionStart && todayStr <= subscriptionEnd) return 'subscribing'

  // 3. 청약 종료 후: DART 예정/거래소 확정 증거가 있으면 공통 표시 상태를 쓴다.
  const listingDisplay = getListingDisplay(ipo, today)
  if (listingDisplay) {
    switch (listingDisplay.kind) {
      case 'unknown':
        return 'listing-unknown'
      case 'estimated':
        return 'listing-scheduled'
      case 'estimated-overdue':
        return 'listing-overdue'
      case 'confirmed':
        return 'listing-confirmed'
    }
  }

  // 4. 증거가 없는 기존 데이터는 날짜 기반 단계를 유지한다.
  if (listingDate) {
    if (todayStr < listingDate) return 'listing-scheduled'
    return 'listed'
  }

  // 상장일이 없으면 상장 여부를 추측하지 않는다.
  return 'listing-unknown'
}

export function getPhaseLabel(phase: IpoPhase): string {
  switch (phase) {
    case 'scheduled':
      return '미청약'
    case 'subscribing':
      return '청약 진행 중'
    case 'listing-scheduled':
      return '상장 예정'
    case 'listing-overdue':
      return '상장예정일 경과 · 거래소 미확인'
    case 'listing-confirmed':
      return '상장 확정'
    case 'listing-unknown':
      return '상장일 미정'
    case 'listed':
      return '상장 완료'
  }
}
