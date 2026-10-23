import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { dismissAnnouncement, fetchPopupAnnouncements } from '@/entities/announcement/index.ts'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import {
  closeAnnouncementForVisit,
  getAnnouncementVisitRevision,
  isAnnouncementClosedForVisit,
  subscribeToAnnouncementVisit,
} from './visitState.ts'
import styles from './AnnouncementPopup.module.css'

export default function AnnouncementPopup() {
  const navigate = useNavigate()
  const { data: account } = useCurrentAccount()
  const query = useQuery({
    queryKey: ['announcement-popup', account?.accountId],
    queryFn: ({ signal }) => fetchPopupAnnouncements(signal),
    enabled: Boolean(account),
  })
  useSyncExternalStore(
    subscribeToAnnouncementVisit,
    getAnnouncementVisitRevision,
    getAnnouncementVisitRevision,
  )
  const [dismissError, setDismissError] = useState(false)
  const closeRef = useRef<HTMLButtonElement>(null)
  const previousFocus = useRef<HTMLElement | null>(null)
  const current =
    query.data?.find((item) => !isAnnouncementClosedForVisit(account?.accountId, item.id)) ?? null
  const announcementId = current?.id
  const dismiss = useMutation({
    mutationFn: (id: number) => dismissAnnouncement(id),
    onSuccess: (_, id) => {
      closeAnnouncementForVisit(account?.accountId, id)
      setDismissError(false)
    },
    onError: () => setDismissError(true),
  })

  useEffect(() => {
    if (!announcementId) {
      previousFocus.current?.focus()
      previousFocus.current = null
      return
    }
    if (!previousFocus.current) {
      previousFocus.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null
    }
    closeRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        closeAnnouncementForVisit(account?.accountId, announcementId)
      }
      if (event.key === 'Tab') {
        const buttons = document.querySelectorAll<HTMLElement>(
          '[data-announcement-dialog] button, [data-announcement-dialog] a',
        )
        const first = buttons[0]
        const last = buttons[buttons.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [announcementId, account?.accountId])

  const closeCurrent = () => {
    if (!current) return
    closeAnnouncementForVisit(account?.accountId, current.id)
    setDismissError(false)
  }

  const openDetail = () => {
    if (!current) return
    closeAnnouncementForVisit(account?.accountId, current.id)
    setDismissError(false)
    const id = current.id
    navigate(`/announcements/${id}`)
  }

  if (!current) return null
  return (
    <div className={styles.overlay}>
      <section
        className={styles.dialog}
        data-announcement-dialog
        role="dialog"
        aria-modal="true"
        aria-labelledby="announcement-title"
        aria-describedby="announcement-content"
      >
        <p className={styles.kicker}>공지사항</p>
        <button ref={closeRef} className={styles.titleButton} type="button" onClick={openDetail}>
          <span id="announcement-title">{current.title}</span>
        </button>
        <p id="announcement-content" className={styles.content}>
          {current.content.slice(0, 200)}
          {current.content.length > 200 ? '…' : ''}
        </p>
        <button type="button" className={styles.detail} onClick={openDetail}>
          자세히 보기
        </button>
        {dismissError ? (
          <p role="alert">다시 보지 않기 설정에 실패했습니다. 다시 시도해 주세요.</p>
        ) : null}
        <div className={styles.actions}>
          <button type="button" onClick={closeCurrent}>
            닫기
          </button>
          <button
            type="button"
            disabled={dismiss.isPending}
            onClick={() => dismiss.mutate(current.id)}
          >
            {dismiss.isPending ? '저장 중...' : '다시 보지 않기'}
          </button>
        </div>
      </section>
    </div>
  )
}
