import styles from './DashboardCard.module.css'

interface DashboardCardProps {
  label: string
  value: number
  format: 'currency' | 'percent' | 'count'
}

function formatValue(value: number, format: string): string {
  switch (format) {
    case 'currency':
      return `${value.toLocaleString()}원`
    case 'percent':
      return `${value.toFixed(1)}%`
    case 'count':
      return `${value}건`
    default:
      return String(value)
  }
}

export default function DashboardCard({ label, value, format }: DashboardCardProps) {
  return (
    <div className={styles.card}>
      <span className={styles.label}>{label}</span>
      <span className={styles.value} aria-label={`${label}: ${formatValue(value, format)}`}>
        {formatValue(value, format)}
      </span>
    </div>
  )
}
