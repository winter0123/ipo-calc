import { useMemo, useState, useCallback, type CSSProperties } from 'react'
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
import styles, { StyleScope } from './IpoCalendar.styles.ts'
import {
  EVENT_TYPE_CLASSES,
  EVENT_TYPE_LABELS,
  barLabel,
  buildWeekRows,
} from './calendarHelpers.ts'
import { SelectedDayDetail } from './IpoCalendarParts.tsx'

const DAYS_OF_WEEK = ['일', '월', '화', '수', '목', '금', '토']

type CalendarGridStyle = CSSProperties & Record<`--calendar-${string}`, string>

const gridStyle = (properties: Record<`--calendar-${string}`, string>): CalendarGridStyle =>
  properties

const DEFAULT_VISIBLE_TYPES: ReadonlySet<CalendarEventType> = new Set([
  'SUBSCRIPTION',
  'REFUND',
  'LISTING',
])
const FIRST_SELECTABLE_YEAR = 2015

interface IpoCalendarProps {
  ipos: Ipo[]
  initialYear?: number
  initialMonth?: number
  visibleTypes?: ReadonlySet<CalendarEventType>
  onVisibleTypesChange?: (next: Set<CalendarEventType>) => void
}

export default function IpoCalendar({
  ipos,
  initialYear,
  initialMonth,
  visibleTypes = DEFAULT_VISIBLE_TYPES,
  onVisibleTypesChange,
}: IpoCalendarProps) {
  const today = new Date()
  const years = Array.from(
    { length: today.getFullYear() - FIRST_SELECTABLE_YEAR + 3 },
    (_, index) => FIRST_SELECTABLE_YEAR + index,
  )
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

  const goToday = () => moveTo(today.getFullYear(), today.getMonth() + 1)

  const handleDateClick = (date: string) => {
    setSelectedDate(date)
  }

  const weekRows = useMemo(
    () => buildWeekRows(startingDayOfWeek, daysInMonth),
    [startingDayOfWeek, daysInMonth],
  )

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
    <StyleScope>
      {
        <div className={styles.calendar}>
          <div className={styles.header}>
            <div className={styles.dateSelectors} aria-label="달력 날짜 선택">
              <label className={styles.srOnly} htmlFor="calendar-year">
                연도
              </label>
              <select
                id="calendar-year"
                aria-label="연도"
                className={styles.dateSelect}
                value={year}
                onChange={(event) => moveTo(Number(event.target.value), month)}
              >
                {years.map((optionYear) => (
                  <option key={optionYear} value={optionYear}>
                    {optionYear}년
                  </option>
                ))}
              </select>
              <label className={styles.srOnly} htmlFor="calendar-month">
                월
              </label>
              <select
                id="calendar-month"
                aria-label="월"
                className={styles.dateSelect}
                value={month}
                onChange={(event) => moveTo(year, Number(event.target.value))}
              >
                {Array.from({ length: 12 }, (_, index) => index + 1).map((optionMonth) => (
                  <option key={optionMonth} value={optionMonth}>
                    {optionMonth}월
                  </option>
                ))}
              </select>
              <span className={styles.srOnly} aria-live="polite">
                {year}년 {month}월
              </span>
            </div>
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
                  style={gridStyle({ '--calendar-lanes': String(laneCount) })}
                >
                  {week.map((day, dayIndex) => {
                    if (day === null) {
                      return (
                        <div
                          key={dayIndex}
                          className={styles.emptyCell}
                          style={gridStyle({ '--calendar-column': String(dayIndex + 1) })}
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
                        style={gridStyle({ '--calendar-column': String(dayIndex + 1) })}
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
                      style={gridStyle({
                        '--calendar-bar-column': `${segment.startCol + 1} / ${segment.endCol + 2}`,
                        '--calendar-bar-row': String(segment.lane + 2),
                        '--calendar-bar-radius-left': segment.continuesBefore ? '0px' : '6px',
                        '--calendar-bar-radius-right': segment.continuesAfter ? '0px' : '6px',
                        '--calendar-bar-margin-left': segment.continuesBefore ? '0px' : '2px',
                        '--calendar-bar-margin-right': segment.continuesAfter ? '0px' : '2px',
                      })}
                      aria-label={barLabel(segment.event)}
                    >
                      {segment.event.companyName}
                    </Link>
                  ))}
                </div>
              )
            })}
          </div>

          <SelectedDayDetail
            selectedDate={selectedDate}
            holidayName={selectedDate ? holidayMap.get(selectedDate) : undefined}
            events={selectedDateEvents}
            today={today}
          />

          {visibleTypes.size === 0 && (
            <p className={styles.noTypesSelected} role="status">
              표시할 일정 종류를 선택해 주세요.
            </p>
          )}
        </div>
      }
    </StyleScope>
  )
}
