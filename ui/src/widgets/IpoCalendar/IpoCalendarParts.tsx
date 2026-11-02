import { Link } from 'react-router-dom'
import { forwardRef } from 'react'
import type { CalendarEvent } from '@/features/ipo-calendar/index.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'
import { useIpoInsight } from '@/entities/ipo/index.ts'
import styles, { StyleScope } from './IpoCalendar.styles.ts'
import { EVENT_TYPE_CLASSES, EVENT_TYPE_LABELS } from './calendarHelpers.ts'

function SubscriptionUnderwriters({ ipoId }: { ipoId: number }) {
  const { data } = useIpoInsight(ipoId)
  const names = [
    ...new Set((data?.underwriters ?? []).map(({ name }) => name.trim()).filter(Boolean)),
  ]
  if (names.length === 0) return null

  return <span className={styles.eventUnderwriters}>주관사 {names.join(' · ')}</span>
}

/** 선택한 날짜의 일정 상세 구역. */
export const SelectedDayDetail = forwardRef<
  HTMLElement,
  {
    selectedDate: string | null
    holidayName: string | undefined
    events: CalendarEvent[]
    today: Date
  }
>(({ selectedDate, holidayName, events, today }, ref) => {
  return (
    <StyleScope>
      {
        <section
          ref={ref}
          className={styles.detail}
          aria-label="선택한 날짜 일정 상세"
          aria-live="polite"
        >
          <h3 className={styles.detailTitle}>{selectedDate ? `${selectedDate} 일정` : ''}</h3>
          {selectedDate && holidayName && (
            <p className={styles.holidayNotice}>휴장일 · {holidayName}</p>
          )}
          {selectedDate && events.length === 0 && (
            <p className={styles.noEvents}>공모주 일정이 없습니다</p>
          )}
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
                    {event.type === 'SUBSCRIPTION' && (
                      <SubscriptionUnderwriters ipoId={event.ipoId} />
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
      }
    </StyleScope>
  )
})

SelectedDayDetail.displayName = 'SelectedDayDetail'
