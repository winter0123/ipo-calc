import { useState } from 'react'
import styles from './PeriodSelector.module.css'

interface PeriodSelectorProps {
  unit: 'month' | 'year'
  initialYear?: number
  onSearch: (year: number) => void
}

const MIN_YEAR = 2000

export default function PeriodSelector({ unit, initialYear, onSearch }: PeriodSelectorProps) {
  const currentYear = new Date().getFullYear()
  const [year, setYear] = useState<number>(initialYear ?? currentYear)
  const [error, setError] = useState<string | null>(null)

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
    setError(null)
    onSearch(inputYear)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch()
  }

  return (
    <div className={styles.container}>
      {unit === 'month' && (
        <span className={styles.currentMonth}>월: {new Date().getMonth() + 1}월</span>
      )}
      <div className={styles.inputGroup}>
        <label htmlFor="period-year" className={styles.label}>
          연도
        </label>
        <input
          id="period-year"
          type="number"
          className={`${styles.input} ${error ? styles.inputError : ''}`}
          value={year}
          onChange={(e) => {
            const val = e.target.value ? Number(e.target.value) : undefined
            setYear(val ?? currentYear)
            setError(null)
          }}
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
      <button type="button" className={styles.button} onClick={handleSearch}>
        조회
      </button>
    </div>
  )
}
