import { useEffect, useRef } from 'react'
import styles, { StyleScope } from './ConfirmDialog.styles.ts'

export interface ConfirmDialogProps {
  isOpen: boolean
  title: string
  message: string | React.ReactNode
  onConfirm: () => void
  onCancel: () => void
  confirmLabel?: string
  cancelLabel?: string
  /** 확인 처리 중이면 확인 버튼을 잠근다(연속 클릭으로 요청이 두 번 나가지 않게). */
  pending?: boolean
}

export default function ConfirmDialog({
  isOpen,
  title,
  message,
  onConfirm,
  onCancel,
  confirmLabel = '확인',
  cancelLabel = '취소',
  pending = false,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const confirmButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!isOpen) return

    confirmButtonRef.current?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCancel()
        return
      }

      if (event.key === 'Tab') {
        const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        )
        if (!focusable || focusable.length === 0) return

        const first = focusable[0]
        const last = focusable[focusable.length - 1]

        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onCancel])

  if (!isOpen) return null

  return (
    <StyleScope>
      {
        <div className={styles.overlay}>
          <div
            ref={dialogRef}
            className={styles.dialog}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-dialog-title"
            aria-describedby="confirm-dialog-message"
          >
            <h2 id="confirm-dialog-title" className={styles.title}>
              {title}
            </h2>
            <p id="confirm-dialog-message" className={styles.message}>
              {message}
            </p>
            <div className={styles.actions}>
              <button type="button" className={styles.cancelButton} onClick={onCancel}>
                {cancelLabel}
              </button>
              <button
                ref={confirmButtonRef}
                type="button"
                className={styles.confirmButton}
                onClick={pending ? undefined : onConfirm}
                disabled={pending}
                aria-busy={pending}
              >
                {confirmLabel}
              </button>
            </div>
          </div>
        </div>
      }
    </StyleScope>
  )
}
