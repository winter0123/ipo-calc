import { useMemo, useState } from 'react'
import { buildCalendarEvents, type CalendarEvent } from '../../features/ipo-calendar/buildCalendarEvents.ts'
import type { Ipo } from '../../entities/ipo/types.ts'
import styles from './IpoCalendar.module.css'

const DAYS_OF_WEEK = ['일', '월', '화', '수', '목', '금', '토']

const EVENT_TYPE_LABELS: Record<CalendarEvent['type'], string> = {
  SUBSCRIPTION_START: '청약 시작',
  SUBSCRIPTION_END: '청약 마감',
  LISTING: '상장',
}

const EVENT_TYPE_CLASSES: Record<CalendarEvent['type'], string> = {
  SUBSCRIPTION_START: styles.eventSubscriptionStart,
  SUBSCRIPTION_END: styles.eventSubscriptionEnd,
  LISTING: styles.eventListing,
}

interface IpoCalendarProps {
  ipos: Ipo[]
  initialYear?: number
  initialMonth?: number
}

export default function IpoCalendar({ ipos, initialYear, initialMonth }: IpoCalendarProps) {
  const today = new Date()
  const [{ year, month }, setCursor] = useState({
    year: initialYear ?? today.getFullYear(),
    month: initialMonth ?? today.getMonth() + 1,
  })
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  const events = useMemo(() => buildCalendarEvents(ipos, year, month), [ipos, year, month])
  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>()
    for (const event of events) {
      const arr = map.get(event.date) ?? []
      arr.push(event)
      map.set(event.date, arr)
    }
    return map
  }, [events])

  const firstDayOfMonth = new Date(year, month - 1, 1)
  const lastDayOfMonth = new Date(year, month, 0)
  const startingDayOfWeek = firstDayOfMonth.getDay()
  const daysInMonth = lastDayOfMonth.getDate()

  const moveTo = (y: number, m: number) => {
    setCursor({ year: y, month: m })
    setSelectedDate(null)
  }

  const prevMonth = () => (month === 1 ? moveTo(year - 1, 12) : moveTo(year, month - 1))

  const nextMonth = () => (month === 12 ? moveTo(year + 1, 1) : moveTo(year, month + 1))

  const goToday = () => moveTo(today.getFullYear(), today.getMonth() + 1)

  const handleDateClick = (date: string) => {
    setSelectedDate(date)
  }

  const selectedDateEvents = selectedDate ? eventsByDate.get(selectedDate) ?? [] : []

  const weekRows = useMemo(() => {
    const rows: (number | null)[][] = []
    let week: (number | null)[] = []

    for (let i = 0; i < startingDayOfWeek; i++) {
      week.push(null)
    }

    for (let day = 1; day <= daysInMonth; day++) {
      week.push(day)
      if (week.length === 7) {
        rows.push(week)
        week = []
      }
    }

    if (week.length > 0) {
      while (week.length < 7) {
        week.push(null)
      }
      rows.push(week)
    }

    return rows
  }, [startingDayOfWeek, daysInMonth])

  const formatDateKey = (day: number) => `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`

  return (
    <div className={styles.calendar}>
      <div className={styles.header}>
        <button type="button" className={styles.navButton} onClick={prevMonth} aria-label="이전 달">
          ‹
        </button>
        <h2 className={styles.monthTitle} aria-live="polite">
          {year}년 {month}월
        </h2>
        <button type="button" className={styles.navButton} onClick={nextMonth} aria-label="다음 달">
          ›
        </button>
        <button type="button" className={styles.todayButton} onClick={goToday}>
          오늘
        </button>
      </div>

      <div className={styles.weekdays} aria-hidden="true">
        {DAYS_OF_WEEK.map((day) => (
          <div key={day} className={styles.weekday}>
            {day}
          </div>
        ))}
      </div>

      <div className={styles.grid}>
        {weekRows.map((week, weekIndex) => (
          <div key={weekIndex} className={styles.week}>
            {week.map((day, dayIndex) => {
              if (day === null) {
                return <div key={dayIndex} className={styles.emptyCell} />
              }

              const dateKey = formatDateKey(day)
              const dayEvents = eventsByDate.get(dateKey) ?? []
              const isSelected = selectedDate === dateKey
              const isToday =
                day === today.getDate() && month === today.getMonth() + 1 && year === today.getFullYear()

              return (
                <button
                  key={dayIndex}
                  type="button"
                  className={`${styles.dateCell} ${isSelected ? styles.selected : ''} ${isToday ? styles.today : ''} ${dayEvents.length > 0 ? styles.hasEvents : ''}`}
                  onClick={() => handleDateClick(dateKey)}
                  aria-pressed={isSelected}
                  aria-current={isToday ? 'date' : undefined}
                  aria-label={`${month}월 ${day}일${dayEvents.length > 0 ? `, 일정 ${dayEvents.length}개` : ''}`}
                >
                  <span className={styles.dateNumber}>{day}</span>
                  {dayEvents.map((event, idx) => (
                    <span key={idx} className={`${styles.event} ${EVENT_TYPE_CLASSES[event.type]}`}>
                      {EVENT_TYPE_LABELS[event.type]}
                    </span>
                  ))}
                </button>
              )
            })}
          </div>
        ))}
      </div>

      <section className={styles.detail} aria-label="선택한 날짜 일정 상세" aria-live="polite">
        <h3 className={styles.detailTitle}>
          {selectedDate ? `${selectedDate} 일정` : '날짜를 선택하세요'}
        </h3>
        {selectedDate && selectedDateEvents.length === 0 && (
          <p className={styles.noEvents}>일정 없음</p>
        )}
        {selectedDate && selectedDateEvents.length > 0 && (
          <ul className={styles.eventList}>
            {selectedDateEvents.map((event, idx) => (
              <li key={idx} className={styles.eventItem}>
                <span className={`${styles.eventType} ${EVENT_TYPE_CLASSES[event.type]}`}>
                  {EVENT_TYPE_LABELS[event.type]}
                </span>
                <span className={styles.eventCompany}>{event.companyName}</span>
                <span className={styles.eventIpo}>({event.ipoName})</span>
                {event.price && <span className={styles.eventPrice}>{event.price}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}