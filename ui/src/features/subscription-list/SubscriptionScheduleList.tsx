import { Link } from 'react-router-dom'
import type { SubscriptionScheduleItem } from './subscriptionSchedule.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import styles, { StyleScope } from './SubscriptionScheduleList.styles.ts'
import { StatusPill } from '@/shared/components/Primitives.tsx'

const STATUS: Record<SubscriptionScheduleItem['status'], string> = {
  before: '청약 전',
  pending: '청약 완료 · 배정 결과 입력 전',
  partial: '일부 결과 입력',
  allocated: '배정 받음',
  unallocated: '미배정 · 결과 확인',
}

function priceLabel(item: SubscriptionScheduleItem): string {
  if (item.ipo.fixedPrice != null && item.ipo.fixedPrice > 0) {
    return formatWon(item.ipo.fixedPrice)
  }
  if (item.ipo.lowerBandPrice != null && item.ipo.lowerBandPrice > 0) {
    const upper = item.ipo.upperBandPrice
    return upper != null && upper > 0
      ? `${formatWon(item.ipo.lowerBandPrice)} ~ ${formatWon(upper)}`
      : `희망가 ${formatWon(item.ipo.lowerBandPrice)} 이상`
  }
  return '미정'
}

export default function SubscriptionScheduleList({
  items,
  investorSelected,
}: {
  items: SubscriptionScheduleItem[]
  investorSelected: boolean
}) {
  if (items.length === 0)
    return (
      <StyleScope>
        {<p className={styles.empty}>선택한 기간에 공모주 일정이 없습니다.</p>}
      </StyleScope>
    )
  return (
    <StyleScope>
      {
        <ul className={styles.list}>
          {items.map((item) => (
            <li className={styles.card} key={item.ipo.id}>
              <header className={styles.header}>
                <div>
                  <h2>{item.ipo.ipoName}</h2>
                  <p>{item.ipo.companyName}</p>
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
                  {investorSelected &&
                    item.status === 'partial' &&
                    ` · ${item.allocatedCount}건 입력 / ${item.pendingCount}건 대기`}
                  {investorSelected &&
                    item.status === 'allocated' &&
                    ` · ${formatShares(item.allocatedQuantity)}`}
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
                  <dt>공모가 / 희망가</dt>
                  <dd>{priceLabel(item)}</dd>
                </div>
                {investorSelected && (
                  <>
                    <div>
                      <dt>내 청약</dt>
                      <dd>
                        {item.subscriptionCount}건 · {formatShares(item.subscriptionQuantity)}
                      </dd>
                    </div>
                    <div>
                      <dt>배정 결과</dt>
                      <dd>
                        {item.allocatedCount}건 입력 · {formatShares(item.allocatedQuantity)} ·{' '}
                        {item.pendingCount}건 대기
                      </dd>
                    </div>
                  </>
                )}
              </dl>
              {investorSelected && item.subscriptionCount === 0 && (
                <Link className={styles.action} to={`/subscriptions/register?ipoId=${item.ipo.id}`}>
                  청약 등록
                </Link>
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
                        이 청약 배정 입력
                      </Link>
                    )}
                  </section>
                ))}
            </li>
          ))}
        </ul>
      }
    </StyleScope>
  )
}
