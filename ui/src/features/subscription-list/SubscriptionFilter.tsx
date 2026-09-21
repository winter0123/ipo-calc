import { useState } from 'react'
import styles from './SubscriptionFilter.module.css'

export type SubscriptionFilterValue = 'all' | 'unallocated' | 'allocated'

interface SubscriptionFilterProps {
  onChange: (value: SubscriptionFilterValue) => void
}

const FILTERS: { value: SubscriptionFilterValue; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'unallocated', label: '미배정' },
  { value: 'allocated', label: '배정완료' },
]

export default function SubscriptionFilter({ onChange }: SubscriptionFilterProps) {
  const [selected, setSelected] = useState<SubscriptionFilterValue>('all')

  const handleClick = (value: SubscriptionFilterValue) => {
    setSelected(value)
    onChange(value)
  }

  return (
    <div className={styles.group} role="group" aria-label="배정 여부 필터">
      {FILTERS.map((filter) => (
        <button
          key={filter.value}
          type="button"
          className={styles.button}
          aria-pressed={selected === filter.value}
          onClick={() => handleClick(filter.value)}
        >
          {filter.label}
        </button>
      ))}
    </div>
  )
}
