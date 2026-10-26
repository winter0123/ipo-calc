import styles, { StyleScope } from './DashboardCard.styles.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { profitToneClass } from '@/shared/lib/profitTone.ts'

interface DashboardCardProps {
  label: string
  value: number
  format: 'currency' | 'percent' | 'count'
  /** 손익 값이면 부호에 따라 색을 입힌다. */
  signed?: boolean
}

function formatValue(value: number, format: string): string {
  switch (format) {
    case 'currency':
      return formatWon(value)
    case 'percent':
      return `${value.toFixed(1)}%`
    case 'count':
      return `${value}건`
    default:
      return String(value)
  }
}

export default function DashboardCard({ label, value, format, signed }: DashboardCardProps) {
  return (
    <StyleScope>
      {
        <div className={styles.card}>
          <span className={styles.label}>{label}</span>
          <span
            className={`${styles.value} ${signed ? profitToneClass(value) : ''}`}
            aria-label={`${label}: ${formatValue(value, format)}`}
          >
            {formatValue(value, format)}
          </span>
        </div>
      }
    </StyleScope>
  )
}
