import { Link, useSearchParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ipoKeys } from '@/entities/ipo/index.ts'
import type { SubscriptionScheduleItem } from './subscriptionSchedule.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import {
  paginateItems,
  getTotalPages,
  clampPage,
  getCurrentPhase,
  getPhaseLabel,
} from './subscriptionSchedule.ts'
import styles, { StyleScope } from './SubscriptionScheduleList.styles.ts'
import { StatusPill } from '@/shared/components/Primitives.tsx'
import { useIpoInsight } from '@/entities/ipo/index.ts'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

function UnderwriterChips({ ipoId }: { ipoId: number }) {
  const { data } = useIpoInsight(ipoId)
  const names = [
    ...new Set(
      (data?.underwriters ?? [])
        .map(({ name }) => formatSecuritiesFirmName(name.trim()))
        .filter(Boolean),
    ),
  ]
  if (names.length === 0) return null
  return (
    <div className={styles.underwriterChips} aria-label="주관사">
      {names.map((name) => (
        <span key={name} className={styles.underwriterChip}>
          {name}
        </span>
      ))}
    </div>
  )
}

const STATUS_LABEL: Record<SubscriptionScheduleItem['status'], string> = {
  before: '미청약',
  pending: '진행 중',
  partial: '일부 입력',
  allocated: '배정됨',
  unallocated: '미배정',
  sold: '매도 완료',
}

function priceLabel(item: SubscriptionScheduleItem): React.ReactNode {
  const ipo = item.ipo

  if (ipo.fixedPrice != null && ipo.fixedPrice > 0) {
    return <span className={styles.priceValue}>{formatWon(ipo.fixedPrice)}</span>
  }

  if (ipo.lowerBandPrice != null && ipo.upperBandPrice != null) {
    return (
      <span className={styles.priceValue}>
        {formatWon(ipo.lowerBandPrice)}~{formatWon(ipo.upperBandPrice)}
      </span>
    )
  }

  return <span className={styles.priceValue}>미정</span>
}

function phaseLabel(ipo: SubscriptionScheduleItem['ipo'], today: Date): React.ReactNode {
  const phase = getCurrentPhase(ipo, today)
  // 청약 예정은 pill로 표시하지 않음
  if (phase === 'scheduled') return null
  const label = getPhaseLabel(phase)
  return <span className={styles.phasePill}>{label}</span>
}

export default function SubscriptionScheduleList({
  items,
  investorSelected,
}: {
  items: SubscriptionScheduleItem[]
  investorSelected: boolean
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [today, setToday] = useState(() => new Date())
  useEffect(() => {
    let timer: number
    const scheduleMidnightRefresh = () => {
      const now = new Date()
      const midnight = new Date(now)
      midnight.setTime(
        Math.floor((now.getTime() + 9 * 3600000) / 86400000) * 86400000 + 86400000 - 9 * 3600000,
      )
      timer = window.setTimeout(
        () => {
          refreshDate()
          scheduleMidnightRefresh()
        },
        midnight.getTime() - now.getTime() + 25,
      )
    }
    const refreshDate = () => {
      setToday(new Date())
      void queryClient.invalidateQueries({ queryKey: ipoKeys.list() })
    }
    scheduleMidnightRefresh()
    window.addEventListener('focus', refreshDate)
    document.addEventListener('visibilitychange', refreshDate)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('focus', refreshDate)
      document.removeEventListener('visibilitychange', refreshDate)
    }
  }, [queryClient])
  const pageParam = searchParams.get('page')
  const currentPage = clampPage(pageParam ? parseInt(pageParam, 10) : 0, getTotalPages(items))
  const totalPages = getTotalPages(items)
  const paginatedItems = paginateItems(items, currentPage)

  const updatePage = (page: number) => {
    const clampedPage = clampPage(page, totalPages)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (clampedPage > 0) next.set('page', String(clampedPage))
        else next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  if (items.length === 0)
    return (
      <StyleScope>
        {<p className={styles.empty}>선택한 기간에 공모주 일정이 없습니다.</p>}
      </StyleScope>
    )

  return (
    <StyleScope>
      {
        <>
          <ul className={styles.list}>
            {paginatedItems.map((item) => (
              <li className={styles.card} key={item.ipo.id}>
                <header className={styles.header}>
                  <div className={styles.titleGroup}>
                    <h2>{item.ipo.ipoName}</h2>
                    <UnderwriterChips ipoId={item.ipo.id} />
                    {phaseLabel(item.ipo, today)}
                  </div>
                  {investorSelected ? (
                    <div className={styles.actionGroup}>
                      <span className={styles.statusBadge}>{STATUS_LABEL[item.status]}</span>
                      <Link className={styles.manageButton} to={`/ipos/${item.ipo.id}/manage`}>
                        관리
                      </Link>
                    </div>
                  ) : (
                    <StatusPill
                      $tone={
                        item.status === 'allocated' || item.status === 'sold'
                          ? 'success'
                          : item.status === 'unallocated'
                            ? 'warning'
                            : item.status === 'before'
                              ? 'info'
                              : 'neutral'
                      }
                    >
                      투자자 선택 필요
                    </StatusPill>
                  )}
                </header>
                <dl className={styles.details}>
                  <div>
                    <dt>청약 기간</dt>
                    <dd>
                      {item.ipo.subscriptionStartDate.slice(0, 10)} ~{' '}
                      {item.ipo.subscriptionEndDate.slice(0, 10)}
                    </dd>
                  </div>
                  <div>
                    <dt>
                      <span className={styles.srOnly}>공모가</span>
                    </dt>
                    <dd>{priceLabel(item)}</dd>
                  </div>
                  {investorSelected && (
                    <>
                      <div>
                        <dt>내 청약</dt>
                        <dd>{formatShares(item.subscriptionQuantity)}</dd>
                      </div>
                      <div>
                        <dt>배정 결과</dt>
                        <dd>{formatShares(item.allocatedQuantity)}</dd>
                      </div>
                    </>
                  )}
                </dl>
                {investorSelected &&
                  item.subscriptions.map((subscription) => (
                    <section className={styles.subscription} key={subscription.id}>
                      <p>
                        {subscription.subscribedAt.slice(0, 10)} ·{' '}
                        {formatSecuritiesFirmName(subscription.securitiesFirmName ?? '증권사 미상')}
                        {subscription.securitiesAccountName
                          ? ` · ${subscription.securitiesAccountName}`
                          : subscription.securitiesAccountId
                            ? ` · 계좌 ${subscription.securitiesAccountId}`
                            : ''}{' '}
                        · {formatShares(subscription.subscriptionQuantity)}
                      </p>
                    </section>
                  ))}
              </li>
            ))}
          </ul>
          {totalPages > 1 && (
            <nav className={styles.pagination} aria-label="공모주 일정 페이지 이동">
              <button
                className={styles.pageButton}
                onClick={() => updatePage(currentPage - 1)}
                disabled={currentPage === 0}
                aria-label="이전 페이지"
              >
                이전
              </button>
              <span className={styles.pageInfo} aria-live="polite">
                {currentPage + 1} / {totalPages}
              </span>
              <button
                className={styles.pageButton}
                onClick={() => updatePage(currentPage + 1)}
                disabled={currentPage === totalPages - 1}
                aria-label="다음 페이지"
              >
                다음
              </button>
            </nav>
          )}
        </>
      }
    </StyleScope>
  )
}
