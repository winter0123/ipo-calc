import { Link, useSearchParams } from 'react-router-dom'
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

function UnderwriterChips({ ipoId }: { ipoId: number }) {
  const { data } = useIpoInsight(ipoId)
  const names = [
    ...new Set((data?.underwriters ?? []).map(({ name }) => name.trim()).filter(Boolean)),
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

const STATUS: Record<SubscriptionScheduleItem['status'], string> = {
  before: '청약 전',
  pending: '청약 완료 · 배정 결과 입력 전',
  partial: '일부 결과 입력',
  allocated: '배정 받음',
  unallocated: '미배정 · 결과 확인',
}

function priceLabel(item: SubscriptionScheduleItem): React.ReactNode {
  const ipo = item.ipo
  const parts: React.ReactNode[] = []

  if (ipo.fixedPrice != null && ipo.fixedPrice > 0) {
    parts.push(
      <span key="fixed" className={styles.priceRow}>
        <span className={styles.priceLabel}>공모가</span>
        <span className={styles.priceValue}>{formatWon(ipo.fixedPrice)}</span>
      </span>,
    )
  }

  if (ipo.lowerBandPrice != null && ipo.lowerBandPrice > 0) {
    const upper = ipo.upperBandPrice
    parts.push(
      <span key="hope" className={styles.priceRow}>
        <span className={styles.priceLabel}>희망가</span>
        <span className={styles.priceValue}>
          {upper != null && upper > 0
            ? `${formatWon(ipo.lowerBandPrice)} ~ ${formatWon(upper)}`
            : `${formatWon(ipo.lowerBandPrice)} 이상`}
        </span>
      </span>,
    )
  }

  if (parts.length === 0) {
    return <span className={styles.priceValue}>미정</span>
  }

  return parts
}

function phaseLabel(ipo: SubscriptionScheduleItem['ipo'], today: Date): React.ReactNode {
  const phase = getCurrentPhase(ipo, today)
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
  const pageParam = searchParams.get('page')
  const currentPage = clampPage(pageParam ? parseInt(pageParam, 10) : 0, getTotalPages(items))
  const totalPages = getTotalPages(items)
  const paginatedItems = paginateItems(items, currentPage)
  const today = new Date()

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
                  <StatusPill
                    $tone={
                      item.status === 'allocated'
                        ? 'success'
                        : item.status === 'unallocated'
                          ? 'warning'
                          : item.status === 'before'
                            ? 'info'
                            : 'neutral'
                    }
                  >
                    {investorSelected ? STATUS[item.status] : '투자자 선택 필요'}
                    {investorSelected && item.status === 'allocated' && (
                      <span className={styles.statusDetail}>
                        {formatShares(item.allocatedQuantity)}
                      </span>
                    )}
                  </StatusPill>
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
                    <dt>가격</dt>
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
                {investorSelected && item.subscriptionCount === 0 && (
                  <div className={styles.actionGroup}>
                    <Link
                      className={styles.action}
                      to={`/subscriptions/register?ipoId=${item.ipo.id}`}
                    >
                      청약 등록
                    </Link>
                    <Link className={styles.saleAction} to={`/sales/register?ipoId=${item.ipo.id}`}>
                      매도 등록
                    </Link>
                  </div>
                )}
                {investorSelected &&
                  item.subscriptions.map((subscription) => (
                    <section className={styles.subscription} key={subscription.id}>
                      <p>
                        {subscription.subscribedAt.slice(0, 10)} ·{' '}
                        {subscription.securitiesFirmName ?? '증권사 미상'}
                        {subscription.securitiesAccountName
                          ? ` · ${subscription.securitiesAccountName}`
                          : subscription.securitiesAccountId
                            ? ` · 계좌 ${subscription.securitiesAccountId}`
                            : ''}{' '}
                        · {formatShares(subscription.subscriptionQuantity)}
                      </p>
                      {subscription.allocation === null && (
                        <Link
                          className={styles.action}
                          to={`/allocations/register?subscriptionId=${subscription.id}`}
                        >
                          배정 등록
                        </Link>
                      )}
                      {subscription.allocation !== null &&
                        (subscription.remainingQuantity ?? 0) > 0 && (
                          <Link
                            className={styles.saleAction}
                            to={`/sales/register?subscriptionId=${subscription.id}`}
                          >
                            매도 등록
                          </Link>
                        )}
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
