import { type Ipo } from '@/entities/ipo/index.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { dateOnly } from '@/shared/lib/date.ts'

export type CalendarEventType = 'SUBSCRIPTION' | 'REFUND' | 'LISTING'

export interface CalendarEvent {
  ipoId: number
  type: CalendarEventType
  companyName: string
  ipoName: string
  startDate: string
  endDate: string
  price?: string
  estimated?: boolean
}

export interface BarSegment {
  event: CalendarEvent
  startCol: number
  endCol: number
  lane: number
  continuesBefore: boolean
  continuesAfter: boolean
}

function getPriceDisplay(ipo: Ipo): string | undefined {
  const { fixedPrice, lowerBandPrice, upperBandPrice } = ipo
  if (fixedPrice !== null && fixedPrice > 0) {
    return formatWon(fixedPrice)
  }
  if (
    lowerBandPrice !== null &&
    upperBandPrice !== null &&
    lowerBandPrice > 0 &&
    upperBandPrice > 0
  ) {
    return `${lowerBandPrice.toLocaleString()}~${upperBandPrice.toLocaleString()}원`
  }
  return undefined
}

export function eventCoversDate(event: CalendarEvent, date: string): boolean {
  return event.startDate <= date && date <= event.endDate
}

export function buildCalendarEvents(ipos: Ipo[], year: number, month: number): CalendarEvent[] {
  const events: CalendarEvent[] = []
  const targetStart = `${year}-${String(month).padStart(2, '0')}-01`
  const lastDay = new Date(year, month, 0).getDate()
  const targetEnd = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`

  for (const ipo of ipos) {
    const subStartDate = dateOnly(ipo.subscriptionStartDate)
    const subEndDate = dateOnly(ipo.subscriptionEndDate)
    const listingDate = ipo.listingDate ? dateOnly(ipo.listingDate) : null
    const refundDate = ipo.refundDate ? dateOnly(ipo.refundDate) : null

    const actualSubEndDate = subEndDate < subStartDate ? subStartDate : subEndDate

    if (subStartDate <= targetEnd && actualSubEndDate >= targetStart) {
      events.push({
        ipoId: ipo.id,
        type: 'SUBSCRIPTION',
        companyName: ipo.companyName,
        ipoName: ipo.ipoName,
        startDate: subStartDate,
        endDate: actualSubEndDate,
        price: getPriceDisplay(ipo),
      })
    }

    if (listingDate && listingDate >= targetStart && listingDate <= targetEnd) {
      events.push({
        ipoId: ipo.id,
        type: 'LISTING',
        companyName: ipo.companyName,
        ipoName: ipo.ipoName,
        startDate: listingDate,
        endDate: listingDate,
        price: getPriceDisplay(ipo),
      })
    }

    if (refundDate && refundDate >= targetStart && refundDate <= targetEnd) {
      events.push({
        ipoId: ipo.id,
        type: 'REFUND',
        companyName: ipo.companyName,
        ipoName: ipo.ipoName,
        startDate: refundDate,
        endDate: refundDate,
        price: getPriceDisplay(ipo),
        estimated: ipo.refundDateEstimated ?? false,
      })
    }
  }

  events.sort((a, b) => {
    const dateCmp = a.startDate.localeCompare(b.startDate)
    if (dateCmp !== 0) return dateCmp
    if (a.type !== b.type) {
      // Order: SUBSCRIPTION < REFUND < LISTING
      const typeOrder: Record<CalendarEventType, number> = {
        SUBSCRIPTION: 0,
        REFUND: 1,
        LISTING: 2,
      }
      return typeOrder[a.type] - typeOrder[b.type]
    }
    return a.ipoId - b.ipoId
  })

  return events
}

export type VisibleTypes = ReadonlySet<CalendarEventType>

export function parseVisibleTypes(searchParams: URLSearchParams): VisibleTypes {
  const typesParam = searchParams.get('types')
  if (typesParam === null) {
    return new Set(['SUBSCRIPTION', 'REFUND', 'LISTING'])
  }
  if (typesParam === '') {
    return new Set()
  }
  const types = typesParam
    .split(',')
    .filter((t) => t === 'SUBSCRIPTION' || t === 'REFUND' || t === 'LISTING')
  if (types.length === 0) {
    return new Set(['SUBSCRIPTION', 'REFUND', 'LISTING'])
  }
  return new Set(types as CalendarEventType[])
}

/**
 * 켜진 일정 종류를 `types` 쿼리 값으로 바꾼다. 모두 켜짐이면 null(쿼리에서 지운다),
 * 모두 꺼짐이면 빈 문자열(`types=`), 그 외에는 쉼표로 이은 값이다.
 */
export function toTypesParam(visible: VisibleTypes): string | null {
  if (visible.size === 3) {
    return null
  }
  if (visible.size === 0) {
    return ''
  }
  // Ensure consistent order: SUBSCRIPTION, REFUND, LISTING
  const ordered = Array.from(visible)
    .sort((a, b) => {
      const order: Record<CalendarEventType, number> = {
        SUBSCRIPTION: 0,
        REFUND: 1,
        LISTING: 2,
      }
      return order[a] - order[b]
    })
    .join(',')
  return ordered
}

export function buildWeekSegments(
  events: CalendarEvent[],
  weekDates: (string | null)[],
): { segments: BarSegment[]; laneCount: number } {
  const segments: BarSegment[] = []
  const laneEndDates: string[] = []

  const sortedEvents = [...events].sort((a, b) => {
    const aStartCol = weekDates.findIndex((d) => d && eventCoversDate(a, d))
    const bStartCol = weekDates.findIndex((d) => d && eventCoversDate(b, d))
    if (aStartCol !== bStartCol) return aStartCol - bStartCol
    const aEndCol =
      weekDates.length - 1 - [...weekDates].reverse().findIndex((d) => d && eventCoversDate(a, d))
    const bEndCol =
      weekDates.length - 1 - [...weekDates].reverse().findIndex((d) => d && eventCoversDate(b, d))
    const aLen = aEndCol - aStartCol
    const bLen = bEndCol - bStartCol
    if (aLen !== bLen) return bLen - aLen
    if (a.type !== b.type) return a.type === 'SUBSCRIPTION' ? -1 : 1
    return a.ipoId - b.ipoId
  })

  for (const event of sortedEvents) {
    let startCol = -1
    let endCol = -1

    for (let i = 0; i < weekDates.length; i++) {
      const date = weekDates[i]
      if (date && eventCoversDate(event, date)) {
        if (startCol === -1) startCol = i
        endCol = i
      }
    }

    if (startCol === -1) continue

    const continuesBefore = event.startDate < weekDates[startCol]!
    const continuesAfter = event.endDate > weekDates[endCol]!

    let lane = 0
    while (true) {
      const conflict = segments.some(
        (s) => s.lane === lane && !(s.endCol < startCol || s.startCol > endCol),
      )
      if (!conflict) break
      lane++
    }

    segments.push({
      event,
      startCol,
      endCol,
      lane,
      continuesBefore,
      continuesAfter,
    })

    if (lane >= laneEndDates.length) {
      laneEndDates.push(event.endDate)
    } else {
      laneEndDates[lane] = event.endDate
    }
  }

  const laneCount = segments.length > 0 ? Math.max(...segments.map((s) => s.lane)) + 1 : 0

  return { segments, laneCount }
}
