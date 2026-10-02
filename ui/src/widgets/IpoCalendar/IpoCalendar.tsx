import { useMemo, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  buildCalendarEvents,
  buildWeekSegments,
  eventCoversDate,
  type CalendarEventType,
  type BarSegment,
} from '@/features/ipo-calendar/index.ts'
import { type Ipo } from '@/entities/ipo/index.ts'
import { useMarketHolidays } from '@/entities/market-holiday/index.ts'
import styles from './IpoCalendar.module.css'

const DAYS_OF_WEEK = ['일', '월', '화', '수', '목', '금', '토']

const EVENT_TYPE_LABELS: Record<CalendarEventType, string> = {
  SUBSCRIPTION: '청약',
  REFUND: '환불',
  LISTING: '상장',
}

const EVENT_TYPE_CLASSES: Record<CalendarEventType, string> = {
  SUBSCRIPTION: styles.barSubscription,
  REFUND: styles.barRefund,
  LISTING: styles.barListing,
}

const DEFAULT_VISIBLE_TYPES: ReadonlySet<CalendarEventType> = new Set([
  'SUBSCRIPTION',
  'REFUND',
  'LISTING',
])

interface IpoCalendarProps {
  ipos: Ipo[]
  initialYear?: number
  initialMonth?: number
  visibleTypes?: ReadonlySet<CalendarEventType>
  onVisibleTypesChange?: (next: Set<CalendarEventType>) => void
}

function getMonthDay(dateStr: string): { month: number; day: number } {
  const [, month, day] = dateStr.split('-').map(Number)
  return { month, day }
}

export default function IpoCalendar({
  ipos,
  initialYear,
  initialMonth,
  visibleTypes = DEFAULT_VISIBLE_TYPES,
  onVisibleTypesChange,
}: IpoCalendarProps) {
  const today = new Date()
  const [{ year, month }, setCursor] = useState({
    year: initialYear ?? today.getFullYear(),
    month: initialMonth ?? today.getMonth() + 1,
  })
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  const { data: holidays = [] } = useMarketHolidays(year)
  const holidayMap = useMemo(() => new Map(holidays.map((h) => [h.date, h.name])), [holidays])

  const allEvents = useMemo(() => buildCalendarEvents(ipos, year, month), [ipos, year, month])

  const events = useMemo(
    () => allEvents.filter((e) => visibleTypes.has(e.type)),
    [allEvents, visibleTypes],
  )

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

  const formatDateKey = useCallback(
    (day: number) => `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`,
    [year, month],
  )

  const weekSegments = useMemo(() => {
    const segmentsByWeek: Map<number, { segments: BarSegment[]; laneCount: number }> = new Map()
    weekRows.forEach((week, weekIndex) => {
      const weekDates: (string | null)[] = week.map((day) =>
        day === null ? null : formatDateKey(day),
      )
      const { segments, laneCount } = buildWeekSegments(events, weekDates)
      segmentsByWeek.set(weekIndex, { segments, laneCount })
    })
    return segmentsByWeek
  }, [events, weekRows, formatDateKey])

  const selectedDateEvents = useMemo(() => {
    if (!selectedDate) return []
    return events.filter((e) => eventCoversDate(e, selectedDate))
  }, [events, selectedDate])

  const toggleType = (type: CalendarEventType) => {
    if (!onVisibleTypesChange) return
    const next = new Set<CalendarEventType>(visibleTypes)
    if (next.has(type)) {
      next.delete(type)
    } else {
      next.add(type)
    }
    onVisibleTypesChange(next)
  }

  const handleShowAll = () => {
    if (!onVisibleTypesChange) return
    onVisibleTypesChange(new Set<CalendarEventType>(['SUBSCRIPTION', 'REFUND', 'LISTING']))
  }

  const allTypesVisible = visibleTypes.size === 3

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

      {onVisibleTypesChange && (
        <div className={styles.filterGroup} role="group" aria-label="일정 종류 필터">
          {(['SUBSCRIPTION', 'REFUND', 'LISTING'] as CalendarEventType[]).map((type) => (
            <button
              key={type}
              type="button"
              className={`${styles.filterButton} ${visibleTypes.has(type) ? styles.filterOn : styles.filterOff}`}
              onClick={() => toggleType(type)}
              aria-pressed={visibleTypes.has(type)}
            >
              <span
                className={`${styles.filterDot} ${EVENT_TYPE_CLASSES[type]}`}
                aria-hidden="true"
              />
              <span className={styles.filterLabel}>{EVENT_TYPE_LABELS[type]}</span>
            </button>
          ))}
          <button
            type="button"
            className={styles.showAllButton}
            onClick={handleShowAll}
            disabled={allTypesVisible}
          >
            모두 보기
          </button>
        </div>
      )}

      <div className={styles.weekdays} aria-hidden="true">
        {DAYS_OF_WEEK.map((day) => (
          <div key={day} className={styles.weekday}>
            {day}
          </div>
        ))}
      </div>

      <div className={styles.grid}>
        {weekRows.map((week, weekIndex) => {
          const { segments, laneCount } = weekSegments.get(weekIndex) ?? {
            segments: [],
            laneCount: 0,
          }
          return (
            <div
              key={weekIndex}
              className={styles.week}
              style={{ '--lanes': laneCount } as React.CSSProperties}
            >
              {week.map((day, dayIndex) => {
                if (day === null) {
                  return (
                    <div
                      key={dayIndex}
                      className={styles.emptyCell}
                      style={{ gridColumn: dayIndex + 1 }}
                    />
                  )
                }

                const dateKey = formatDateKey(day)
                const dayEvents = events.filter((e) => eventCoversDate(e, dateKey))
                const holidayName = holidayMap.get(dateKey)
                const isSelected = selectedDate === dateKey
                const isToday =
                  day === today.getDate() &&
                  month === today.getMonth() + 1 &&
                  year === today.getFullYear()

                const ariaLabelParts = [`${month}월 ${day}일`]
                if (holidayName) {
                  ariaLabelParts.push(holidayName)
                }
                if (dayEvents.length > 0) {
                  ariaLabelParts.push(`일정 ${dayEvents.length}개`)
                }

                return (
                  <button
                    key={dayIndex}
                    type="button"
                    className={`${styles.dateCell} ${isSelected ? styles.selected : ''} ${isToday ? styles.today : ''} ${dayEvents.length > 0 ? styles.hasEvents : ''} ${holidayName ? styles.hasHoliday : ''}`}
                    style={{ gridColumn: dayIndex + 1 }}
                    onClick={() => handleDateClick(dateKey)}
                    aria-pressed={isSelected}
                    aria-current={isToday ? 'date' : undefined}
                    aria-label={ariaLabelParts.join(', ')}
                  >
                    <span className={styles.dateHead}>
                      {holidayName && <span className={styles.holidayName}>{holidayName}</span>}
                      <span className={styles.dateNumber}>{day}</span>
                    </span>
                  </button>
                )
              })}
              {segments.map((segment) => (
                <Link
                  key={`${segment.event.ipoId}-${segment.startCol}-${segment.endCol}`}
                  to={`/ipos/${segment.event.ipoId}`}
                  className={`${styles.bar} ${EVENT_TYPE_CLASSES[segment.event.type]} ${segment.event.estimated ? styles.barEstimated : ''}`}
                  style={
                    {
                      gridColumn: `${segment.startCol + 1} / ${segment.endCol + 2}`,
                      gridRow: segment.lane + 2,
                      borderTopLeftRadius: segment.continuesBefore ? 0 : '6px',
                      borderBottomLeftRadius: segment.continuesBefore ? 0 : '6px',
                      borderTopRightRadius: segment.continuesAfter ? 0 : '6px',
                      borderBottomRightRadius: segment.continuesAfter ? 0 : '6px',
                      marginLeft: segment.continuesBefore ? 0 : '2px',
                      marginRight: segment.continuesAfter ? 0 : '2px',
                    } as React.CSSProperties
                  }
                  aria-label={
                    segment.event.type === 'SUBSCRIPTION'
                      ? `${segment.event.companyName} 청약 ${getMonthDay(segment.event.startDate).month}월 ${getMonthDay(segment.event.startDate).day}일부터 ${getMonthDay(segment.event.endDate).month}월 ${getMonthDay(segment.event.endDate).day}일까지`
                      : segment.event.type === 'REFUND'
                        ? `${segment.event.companyName} 환불 ${getMonthDay(segment.event.startDate).month}월 ${getMonthDay(segment.event.startDate).day}일${
                            segment.event.estimated ? ' (예상)' : ''
                          }`
                        : `${segment.event.companyName} 상장 ${getMonthDay(segment.event.startDate).month}월 ${getMonthDay(segment.event.startDate).day}일`
                  }
                >
                  {segment.event.companyName}
                </Link>
              ))}
            </div>
          )
        })}
      </div>

      <section className={styles.detail} aria-label="선택한 날짜 일정 상세" aria-live="polite">
        <h3 className={styles.detailTitle}>
          {selectedDate ? `${selectedDate} 일정` : '날짜를 선택하세요'}
        </h3>
        {selectedDate && holidayMap.has(selectedDate) && (
          <p className={styles.holidayNotice}>휴장일 · {holidayMap.get(selectedDate)}</p>
        )}
        {selectedDate && selectedDateEvents.length === 0 && (
          <p className={styles.noEvents}>일정 없음</p>
        )}
        {selectedDate && selectedDateEvents.length > 0 && (
          <ul className={styles.eventList}>
            {selectedDateEvents.map((event, idx) => (
              <li key={idx} className={styles.eventItem}>
                <Link
                  to={`/ipos/${event.ipoId}`}
                  className={styles.eventLink}
                  aria-label={`${event.companyName} ${event.ipoName} 상세 보기`}
                >
                  <span className={`${styles.eventType} ${EVENT_TYPE_CLASSES[event.type]}`}>
                    {EVENT_TYPE_LABELS[event.type]}
                  </span>
                  <span className={styles.eventCompany}>{event.companyName}</span>
                  {event.type === 'REFUND' && event.estimated && (
                    <span className={styles.eventIpo}> (예상)</span>
                  )}
                  <span className={styles.eventIpo}>({event.ipoName})</span>
                  {event.type === 'SUBSCRIPTION' && (
                    <span className={styles.eventPeriod}>
                      {event.startDate} ~ {event.endDate}
                    </span>
                  )}
                  <>
                    {event.price && <span className={styles.eventPrice}>{event.price}</span>}
                    <span className={styles.eventArrow} aria-hidden="true">
                      ›
                    </span>
                  </>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {visibleTypes.size === 0 && (
        <p className={styles.noTypesSelected} role="status">
          표시할 일정 종류를 선택해 주세요.
        </p>
      )}
    </div>
  )
}
