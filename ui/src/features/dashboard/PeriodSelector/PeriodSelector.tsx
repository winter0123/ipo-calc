import { useState, useCallback, useRef, useEffect } from 'react'
import styles from './PeriodSelector.module.css'

interface PeriodSelectorProps {
  unit: 'month' | 'year'
  initialYear?: number
  initialMonth?: number
  onSearch: (year: number, month?: number) => void
}

const MIN_YEAR = 2000

export default function PeriodSelector({
  unit,
  initialYear,
  initialMonth,
  onSearch,
}: PeriodSelectorProps) {
  const now = new Date()
  const currentYear = now.getFullYear()
  const currentMonth = now.getMonth() + 1

  const [year, setYear] = useState<number>(initialYear ?? currentYear)
  const [month, setMonth] = useState<number>(initialMonth ?? currentMonth)
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState<string>('')

  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearAnnouncementTimeout = useCallback(() => {
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => {
      clearAnnouncementTimeout()
    }
  }, [clearAnnouncementTimeout])

  const isFutureOrCurrentPeriod =
    year > currentYear ||
    (year === currentYear && unit === 'month' && month >= currentMonth) ||
    (unit === 'year' && year >= currentYear)

  const announceChange = useCallback(
    (newYear: number, newMonth: number) => {
      const label = unit === 'month' ? `${newYear}년 ${newMonth}월` : `${newYear}년`
      setAnnouncement(`${label} 데이터로 조회됨`)
      clearAnnouncementTimeout()
      timeoutRef.current = setTimeout(() => setAnnouncement(''), 1000)
    },
    [unit, clearAnnouncementTimeout],
  )

  const handleSearch = () => {
    const inputYear = parseInt(String(year), 10)
    if (isNaN(inputYear) || String(inputYear).length !== 4) {
      setError('4자리 숫자로 입력해 주세요.')
      return
    }
    if (inputYear < MIN_YEAR || inputYear > currentYear + 1) {
      setError(`${MIN_YEAR}~${currentYear + 1} 사이의 연도를 입력해 주세요.`)
      return
    }
    if (unit === 'month' && (month < 1 || month > 12)) {
      setError('1~12 사이의 월을 입력해 주세요.')
      return
    }
    setError(null)
    announceChange(inputYear, unit === 'month' ? month : currentMonth)
    onSearch(inputYear, unit === 'month' ? month : undefined)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch()
  }

  const handlePrevPeriod = () => {
    setError(null)
    if (unit === 'month') {
      if (month === 1) {
        const newYear = Math.max(MIN_YEAR, year - 1)
        const newMonth = 12
        setMonth(newMonth)
        setYear(newYear)
        announceChange(newYear, newMonth)
        onSearch(newYear, newMonth)
      } else {
        const newMonth = month - 1
        setMonth(newMonth)
        announceChange(year, newMonth)
        onSearch(year, newMonth)
      }
    } else {
      const newYear = Math.max(MIN_YEAR, year - 1)
      setYear(newYear)
      announceChange(newYear, currentMonth)
      onSearch(newYear)
    }
  }

  const handleNextPeriod = () => {
    setError(null)
    if (unit === 'month') {
      if (month === 12) {
        if (year < currentYear) {
          const newYear = year + 1
          const newMonth = 1
          setMonth(newMonth)
          setYear(newYear)
          announceChange(newYear, newMonth)
          onSearch(newYear, newMonth)
        }
      } else if (year < currentYear || (year === currentYear && month < currentMonth)) {
        const newMonth = month + 1
        setMonth(newMonth)
        announceChange(year, newMonth)
        onSearch(year, newMonth)
      }
    } else if (year < currentYear) {
      const newYear = year + 1
      setYear(newYear)
      announceChange(newYear, currentMonth)
      onSearch(newYear)
    }
  }

  const handleYearChange = (val: number | undefined) => {
    setYear(val ?? currentYear)
    setError(null)
  }

  const handleMonthChange = (val: number) => {
    setMonth(val)
    setError(null)
  }

  return (
    <div className={styles.container}>
      <div id="period-announcer" className={styles.announcer} aria-live="polite" aria-atomic="true">
        {announcement}
      </div>

      <h2 id="period-heading" className={styles.heading}>
        {unit === 'month' ? `${year}년 ${month}월` : `${year}년`}
      </h2>

      <div className={styles.controls}>
        <button
          type="button"
          className={styles.navButton}
          onClick={handlePrevPeriod}
          aria-label={unit === 'month' ? '이전 달로 이동' : '이전 연도로 이동'}
          disabled={year <= MIN_YEAR && (unit !== 'month' || month <= 1)}
        >
          {unit === 'month' ? '◀ 이전 달' : '◀ 이전 연도'}
        </button>

        <div className={styles.inputGroup}>
          <label htmlFor="period-year" className={styles.label}>
            연도
          </label>
          <input
            id="period-year"
            type="number"
            className={`${styles.input} ${error ? styles.inputError : ''}`}
            value={year}
            onChange={(e) => handleYearChange(e.target.value ? Number(e.target.value) : undefined)}
            onKeyDown={handleKeyDown}
            aria-label="연도 입력"
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'period-year-error' : undefined}
            min={MIN_YEAR}
            max={currentYear + 1}
          />
          {error && (
            <span id="period-year-error" className={styles.error} role="alert">
              {error}
            </span>
          )}
        </div>

        {unit === 'month' && (
          <div className={styles.inputGroup}>
            <label htmlFor="period-month" className={styles.label}>
              월
            </label>
            <select
              id="period-month"
              className={`${styles.select} ${error ? styles.inputError : ''}`}
              value={month}
              onChange={(e) => handleMonthChange(Number(e.target.value))}
              aria-label="월 선택"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? 'period-month-error' : undefined}
            >
              {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                <option key={m} value={m}>
                  {m}월
                </option>
              ))}
            </select>
            {error && (
              <span id="period-month-error" className={styles.error} role="alert">
                {error}
              </span>
            )}
          </div>
        )}

        <button
          type="button"
          className={styles.navButton}
          onClick={handleNextPeriod}
          aria-label={unit === 'month' ? '다음 달로 이동' : '다음 연도로 이동'}
          disabled={isFutureOrCurrentPeriod}
        >
          {unit === 'month' ? '다음 달 ▶' : '다음 연도 ▶'}
        </button>

        <button type="button" className={styles.button} onClick={handleSearch}>
          조회
        </button>
      </div>
    </div>
  )
}
