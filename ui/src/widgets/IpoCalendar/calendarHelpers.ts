import type { CalendarEvent, CalendarEventType } from '@/features/ipo-calendar/index.ts'
import styles from './IpoCalendar.module.css'

export const EVENT_TYPE_LABELS: Record<CalendarEventType, string> = {
  SUBSCRIPTION: '청약',
  REFUND: '환불',
  LISTING: '상장',
}

export const EVENT_TYPE_CLASSES: Record<CalendarEventType, string> = {
  SUBSCRIPTION: styles.barSubscription,
  REFUND: styles.barRefund,
  LISTING: styles.barListing,
}

/** 해당 달을 일요일 시작 주 단위(7칸, 빈 칸은 null)로 나눈다. */
export function buildWeekRows(startingDayOfWeek: number, daysInMonth: number): (number | null)[][] {
  const rows: (number | null)[][] = []
  let week: (number | null)[] = Array(startingDayOfWeek).fill(null)
  for (let day = 1; day <= daysInMonth; day++) {
    week.push(day)
    if (week.length === 7) {
      rows.push(week)
      week = []
    }
  }
  if (week.length > 0) {
    while (week.length < 7) week.push(null)
    rows.push(week)
  }
  return rows
}

function getMonthDay(dateStr: string): { month: number; day: number } {
  const [, month, day] = dateStr.split('-').map(Number)
  return { month, day }
}

/** 달력 막대(링크)의 스크린리더용 이름. */
export function barLabel(event: CalendarEvent): string {
  const start = getMonthDay(event.startDate)
  const startText = `${start.month}월 ${start.day}일`
  if (event.type === 'SUBSCRIPTION') {
    const end = getMonthDay(event.endDate)
    return `${event.companyName} 청약 ${startText}부터 ${end.month}월 ${end.day}일까지`
  }
  if (event.type === 'REFUND') {
    return `${event.companyName} 환불 ${startText}${event.estimated ? ' (예상)' : ''}`
  }
  return `${event.companyName} 상장 ${startText}`
}
