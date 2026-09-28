import styles from './ErrorMessage.module.css'

export interface ErrorMessageProps {
  message?: string | null
  id?: string
  className?: string
}

export default function ErrorMessage({ message, id, className }: ErrorMessageProps) {
  if (!message) return null

  const classNames = [styles.error, className].filter(Boolean).join(' ')

  return (
    <p id={id} role="alert" className={classNames}>
      {message}
    </p>
  )
}
