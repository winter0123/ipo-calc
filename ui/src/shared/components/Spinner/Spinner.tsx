import styles from './Spinner.module.css'

export interface SpinnerProps {
  label?: string
  className?: string
}

export default function Spinner({ label = '로딩 중...', className }: SpinnerProps) {
  const classNames = [styles.spinner, className].filter(Boolean).join(' ')

  return (
    <div className={classNames} role="status" aria-live="polite">
      <span className={styles.indicator} aria-hidden="true" />
      <span className={styles.label}>{label}</span>
    </div>
  )
}
