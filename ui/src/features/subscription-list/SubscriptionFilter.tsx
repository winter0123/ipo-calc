import styles, { StyleScope } from './SubscriptionFilter.styles.ts'
import { FILTERS, type SubscriptionFilterValue } from './filterValue.ts'

interface SubscriptionFilterProps {
  value: SubscriptionFilterValue
  onChange: (value: SubscriptionFilterValue) => void
}

export default function SubscriptionFilter({ value, onChange }: SubscriptionFilterProps) {
  return (
    <StyleScope>
      {
        <div className={styles.group} role="group" aria-label="배정 여부 필터">
          {FILTERS.map((filter) => (
            <button
              key={filter.value}
              type="button"
              className={styles.button}
              aria-pressed={value === filter.value}
              onClick={() => onChange(filter.value)}
            >
              {filter.label}
            </button>
          ))}
        </div>
      }
    </StyleScope>
  )
}
