import { useEffect, useRef, type FormEventHandler, type ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import styles, { StyleScope } from './RecordActions.styles.ts'

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
  const dialogRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  // onClose는 매 렌더 새로 만들어지므로 ref로 읽어, 렌더마다 포커스 이동이 다시 일어나지 않게 한다
  useEffect(() => {
    closeRef.current = onClose
  })

  // 열릴 때 대화상자로 포커스를 옮기고, Tab을 안에 가두고, Esc로 닫고, 닫히면 열기 전 위치로 포커스를 돌려준다.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    dialogRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') return closeRef.current()
      if (e.key !== 'Tab') return
      const items = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]',
      )
      if (!items || items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (e.shiftKey && (active === first || active === dialogRef.current)) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && active === last) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      opener?.focus()
    }
  }, [])

  return (
    <StyleScope>
      {
        <div className={styles.overlay}>
          <div
            ref={dialogRef}
            tabIndex={-1}
            className={styles.dialog}
            role="dialog"
            aria-modal="true"
            aria-label={title}
          >
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
      }
    </StyleScope>
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
    <StyleScope>
      {
        <div className={styles.field}>
          <label htmlFor={id}>{label}</label>
          {children}
          {error && (
            <span className={styles.fieldError} aria-live="polite">
              {error}
            </span>
          )}
        </div>
      }
    </StyleScope>
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
    <StyleScope>
      {
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
      }
    </StyleScope>
  )
}
