import { useId } from 'react'
import type { ReactNode } from 'react'
import ErrorMessage from '../ErrorMessage/ErrorMessage.tsx'
import styles from './FormField.module.css'

export interface FormFieldProps {
  label: string
  htmlFor?: string
  children: ReactNode
  error?: string
  className?: string
}

export default function FormField({ label, htmlFor, children, error, className }: FormFieldProps) {
  const generatedId = useId()
  const errorId = htmlFor ? `${htmlFor}-error` : `${generatedId}-error`
  const classNames = [styles.field, className].filter(Boolean).join(' ')

  return (
    <div className={classNames}>
      <label className={styles.label} htmlFor={htmlFor}>
        {label}
      </label>
      <div className={styles.control}>{children}</div>
      <ErrorMessage id={errorId} message={error} />
    </div>
  )
}
