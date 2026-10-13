import { useEffect, type FormEventHandler, type ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import styles from './RecordActions.module.css'

/** 레코드를 읽어 온 뒤 폼을 보여 주는 대화상자 껍데기. */
export function Shell<T>({
  title,
  query,
  onClose,
  children,
}: {
  title: string
  query: UseQueryResult<T>
  onClose: () => void
  children: (record: T) => ReactNode
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className={styles.overlay}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-label={title}>
        <h2 className={styles.dialogTitle}>{title}</h2>
        {query.data ? (
          children(query.data)
        ) : query.isError ? (
          <p role="alert" className={styles.error}>
            {toErrorMessage(query.error)}
          </p>
        ) : (
          <p>불러오는 중…</p>
        )}
        {!query.data && (
          <div className={styles.formActions}>
            <button type="button" className={styles.smallButton} onClick={onClose}>
              닫기
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export function Field({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className={styles.field}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error && (
        <span className={styles.fieldError} aria-live="polite">
          {error}
        </span>
      )}
    </div>
  )
}

/** 필드들 + 서버 오류 + 취소/저장 버튼을 담은 폼 틀. */
export function EditForm({
  onSubmit,
  pending,
  error,
  onClose,
  children,
}: {
  onSubmit: FormEventHandler<HTMLFormElement>
  pending: boolean
  error: unknown
  onClose: () => void
  children: ReactNode
}) {
  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      {children}
      {!!error && (
        <p role="alert" className={styles.error}>
          {toErrorMessage(error)}
        </p>
      )}
      <div className={styles.formActions}>
        <button type="button" className={styles.smallButton} onClick={onClose}>
          취소
        </button>
        <button type="submit" className={styles.smallButton} disabled={pending}>
          {pending ? '저장 중…' : '저장'}
        </button>
      </div>
    </form>
  )
}
