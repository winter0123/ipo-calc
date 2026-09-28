import { useId } from 'react'
import type { InputHTMLAttributes } from 'react'
import ErrorMessage from '../ErrorMessage/ErrorMessage.tsx'
import styles from './Input.module.css'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
}

export default function Input({ label, error, id, className, ...rest }: InputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`
  const classNames = [styles.input, className].filter(Boolean).join(' ')

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      <input
        {...rest}
        id={inputId}
        className={classNames}
        {...(error ? { 'aria-invalid': true, 'aria-describedby': errorId } : {})}
      />
      <ErrorMessage id={errorId} message={error} className={styles.error} />
    </div>
  )
}
