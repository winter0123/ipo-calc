import { type ChangeEvent } from 'react'
import styles from './ProfitQueryToggle.module.css'

type QueryMode = 'sale' | 'investor'

interface ProfitQueryToggleProps {
  mode: QueryMode
  onModeChange: (mode: QueryMode) => void
}

export default function ProfitQueryToggle({ mode, onModeChange }: ProfitQueryToggleProps) {
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onModeChange(e.target.value as QueryMode)
  }

  return (
    <div className={styles.toggle} role="radiogroup" aria-label="수익 조회 모드">
      <label className={styles.option}>
        <input
          type="radio"
          name="profit-query-mode"
          value="sale"
          checked={mode === 'sale'}
          onChange={handleChange}
        />
        <span>매도건별</span>
      </label>
      <label className={styles.option}>
        <input
          type="radio"
          name="profit-query-mode"
          value="investor"
          checked={mode === 'investor'}
          onChange={handleChange}
        />
        <span>투자자별</span>
      </label>
    </div>
  )
}
