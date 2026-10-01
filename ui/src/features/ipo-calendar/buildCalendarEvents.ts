import { type Ipo } from '@/entities/ipo/index.ts'

export type CalendarEventType = 'SUBSCRIPTION_START' | 'SUBSCRIPTION_END' | 'LISTING'

export interface CalendarEvent {
  date: string
  type: CalendarEventType
  companyName: string
  ipoName: string
  price?: string
}

function parseDateOnly(dateStr: string): string {
  return dateStr.split('T')[0]
}

function getPriceDisplay(ipo: Ipo): string | undefined {
  const { fixedPrice, lowerBandPrice, upperBandPrice } = ipo
  if (fixedPrice !== null && fixedPrice > 0) {
    return `${fixedPrice.toLocaleString()}원`
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

export function buildCalendarEvents(ipos: Ipo[], year: number, month: number): CalendarEvent[] {
  const events: CalendarEvent[] = []
  const targetYear = year
  const targetMonth = month

  for (const ipo of ipos) {
    const subStartDate = parseDateOnly(ipo.subscriptionStartDate)
    const subEndDate = parseDateOnly(ipo.subscriptionEndDate)
    const listingDate = ipo.listingDate ? parseDateOnly(ipo.listingDate) : null

    const [subStartYear, subStartMonth] = subStartDate.split('-').map(Number)
    const [subEndYear, subEndMonth] = subEndDate.split('-').map(Number)

    if (subStartYear === targetYear && subStartMonth === targetMonth) {
      events.push({
        date: subStartDate,
        type: 'SUBSCRIPTION_START',
        companyName: ipo.companyName,
        ipoName: ipo.ipoName,
        price: getPriceDisplay(ipo),
      })
    }

    if (subEndYear === targetYear && subEndMonth === targetMonth) {
      events.push({
        date: subEndDate,
        type: 'SUBSCRIPTION_END',
        companyName: ipo.companyName,
        ipoName: ipo.ipoName,
        price: getPriceDisplay(ipo),
      })
    }

    if (listingDate) {
      const [listingYear, listingMonth] = listingDate.split('-').map(Number)
      if (listingYear === targetYear && listingMonth === targetMonth) {
        events.push({
          date: listingDate,
          type: 'LISTING',
          companyName: ipo.companyName,
          ipoName: ipo.ipoName,
          price: getPriceDisplay(ipo),
        })
      }
    }
  }

  events.sort((a, b) => a.date.localeCompare(b.date))
  return events
}
