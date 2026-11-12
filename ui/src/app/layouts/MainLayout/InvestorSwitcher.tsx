import { ChevronDown } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useUnsavedGuard } from '@/shared/navigation/unsavedGuard.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import styles, { StyleScope } from './MainLayout.styles.ts'

// 현재 투자자 전환기. 투자자가 없으면 보이지 않고, 여러 명이면 버튼 아래 목록을 연다.
// 미저장 입력이 있으면 전환 전에 확인을 받고, 저장이 진행 중이면 전환을 막는다.
export default function InvestorSwitcher() {
  const { investors, current, select } = useCurrentInvestor()
  const { isDirty, isPending } = useUnsavedGuard()
  const [open, setOpen] = useState(false)
  // 전환 전에 확인이 필요한 투자자(미저장 입력이 있는 경우)
  const [pendingInvestorId, setPendingInvestorId] = useState<number | null>(null)
  // 저장이 진행 중이라 전환할 수 없는 경우 안내
  const [blockedPending, setBlockedPending] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const closeOnOutside = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setOpen(false)
      buttonRef.current?.focus()
    }
    document.addEventListener('pointerdown', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [investors.length, open])
  if (investors.length === 0) return null

  const name = current?.displayName ?? '투자자 선택'
  const avatar = (
    <span className={styles.avatar} aria-hidden="true">
      {current ? current.displayName.slice(0, 1) : '?'}
    </span>
  )

  if (investors.length === 1) {
    return (
      <StyleScope>
        {
          <span className={styles.investorChip}>
            {avatar}
            <span className={styles.srOnly}>현재 투자자: </span>
            <span className={styles.chipName}>{name}</span>
          </span>
        }
      </StyleScope>
    )
  }

  const requestSelect = (investorId: number) => {
    if (investorId === current?.id) {
      setOpen(false)
      return
    }
    if (isPending) {
      // 저장·필수 재조회가 끝나기 전에는 전환할 수 없다(뒤로 가기 경고와 같은 기준).
      setOpen(false)
      setBlockedPending(true)
      return
    }
    if (isDirty) {
      // 미저장 입력이 있으면 현재 유지/전환 확인을 받는다. 확인 시에만 문맥을 새로 적용한다.
      setOpen(false)
      setPendingInvestorId(investorId)
      return
    }
    select(investorId)
    setOpen(false)
    buttonRef.current?.focus()
  }

  const confirmSwitch = () => {
    const target = pendingInvestorId
    setPendingInvestorId(null)
    if (target === null) return
    select(target)
    buttonRef.current?.focus()
  }

  return (
    <StyleScope>
      {
        <div
          ref={rootRef}
          role="group"
          aria-label="투자자 선택"
          tabIndex={0}
          className={styles.investorSwitcher}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOpen(false)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') setOpen(false)
          }}
        >
          <button
            ref={buttonRef}
            type="button"
            className={`${styles.investorChip} ${styles.investorChipButton}`}
            aria-label={`현재 투자자: ${name}`}
            aria-expanded={open}
            aria-controls="investor-switcher-options"
            aria-haspopup="true"
            onClick={() => setOpen((value) => !value)}
          >
            {avatar}
            <span className={styles.srOnly}>현재 투자자: </span>
            <span className={styles.chipName}>{name}</span>
            <ChevronDown aria-hidden="true" size={16} className={styles.chevron} />
          </button>
          {open && (
            <div
              id="investor-switcher-options"
              className={styles.investorOptions}
              role="group"
              aria-label="투자자 선택"
            >
              {investors.map((investor) => (
                <button
                  key={investor.id}
                  type="button"
                  className={styles.investorOption}
                  aria-pressed={investor.id === current?.id}
                  onClick={() => requestSelect(investor.id)}
                >
                  {investor.displayName}
                </button>
              ))}
            </div>
          )}
        </div>
      }
      <ConfirmDialog
        isOpen={pendingInvestorId !== null}
        title="저장하지 않은 입력이 있습니다"
        message="다른 투자자로 바꾸면 지금 입력한 내용이 지워집니다. 그래도 바꿀까요?"
        confirmLabel="바꾸기"
        cancelLabel="계속 작성"
        onConfirm={confirmSwitch}
        onCancel={() => {
          setPendingInvestorId(null)
          buttonRef.current?.focus()
        }}
      />
      <ConfirmDialog
        isOpen={blockedPending}
        title="저장이 진행 중입니다"
        message="저장이 끝나기 전에는 투자자를 바꿀 수 없습니다. 저장이 끝난 뒤 다시 시도해 주세요."
        confirmLabel="확인"
        cancelLabel="계속 작성"
        pending
        onConfirm={() => setBlockedPending(false)}
        onCancel={() => setBlockedPending(false)}
      />
    </StyleScope>
  )
}
