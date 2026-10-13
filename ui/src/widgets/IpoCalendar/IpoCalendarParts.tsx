import { Link } from 'react-router-dom'
import type { CalendarEvent } from '@/features/ipo-calendar/index.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'
import styles from './IpoCalendar.module.css'
import { EVENT_TYPE_CLASSES, EVENT_TYPE_LABELS } from './calendarHelpers.ts'

/** 선택한 날짜의 일정 상세 구역. */
export function SelectedDayDetail({
  selectedDate,
  holidayName,
  events,
  today,
}: {
  selectedDate: string | null
  holidayName: string | undefined
  events: CalendarEvent[]
  today: Date
}) {
  return (
    <section className={styles.detail} aria-label="선택한 날짜 일정 상세" aria-live="polite">
      <h3 className={styles.detailTitle}>
        {selectedDate ? `${selectedDate} 일정` : '날짜를 선택하세요'}
      </h3>
      {selectedDate && holidayName && (
        <p className={styles.holidayNotice}>휴장일 · {holidayName}</p>
      )}
      {selectedDate && events.length === 0 && <p className={styles.noEvents}>일정 없음</p>}
      {selectedDate && events.length > 0 && (
        <ul className={styles.eventList}>
          {events.map((event, idx) => (
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
              {event.type === 'SUBSCRIPTION' && event.endDate >= toLocalYmd(today) && (
                <Link
                  to={`/subscriptions/register?ipoId=${event.ipoId}`}
                  className={styles.registerLink}
                  aria-label={`${event.companyName} 청약 등록`}
                >
                  청약 등록
                </Link>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
