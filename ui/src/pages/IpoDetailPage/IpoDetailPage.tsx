import { useParams, Link } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useIpo, useIpoInsight } from '@/entities/ipo/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Spinner from '@/shared/components/Spinner/Spinner.tsx'
import ErrorMessage from '@/shared/components/ErrorMessage/ErrorMessage.tsx'
import styles, { StyleScope } from './IpoDetailPage.styles.ts'
import IpoInsightSections from './IpoInsightSections.tsx'
import { dateOnly, toLocalYmd } from '@/shared/lib/date.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'

export default function IpoDetailPage() {
  const { ipoId } = useParams<{ ipoId: string }>()
  const id = ipoId ? Number(ipoId) : NaN
  const isValidId = !Number.isNaN(id) && id > 0

  const { data: ipo, isLoading, error } = useIpo(isValidId ? id : undefined)
  const { data: insight } = useIpoInsight(isValidId ? id : undefined)
  const { data: account, isPending: accountPending } = useCurrentAccount()
  const isGuest = !accountPending && !account

  const getErrorMessage = () =>
    toErrorMessage(error, {
      byStatus: {
        404: '공모주를 찾을 수 없습니다.',
      },
      fallback: '공모주 정보를 불러오지 못했습니다.',
    })

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '-'
    return dateOnly(dateStr)
  }

  const formatPrice = (price: number | null) => {
    if (price === null || price === 0) return '-'
    return formatWon(price)
  }

  const formatBandPrice = (lower: number | null, upper: number | null) => {
    if (lower !== null && upper !== null) {
      return `${lower.toLocaleString()}~${upper.toLocaleString()}원`
    }
    if (lower !== null) {
      return formatWon(lower)
    }
    if (upper !== null) {
      return formatWon(upper)
    }
    return '-'
  }

  const formatShareCount = (count: number | null) => {
    if (count === null) return '-'
    return formatShares(count)
  }

  const getStatus = (ipoData: typeof ipo) => {
    if (!ipoData) return { label: '알 수 없음', className: styles.statusUnknown }
    const today = toLocalYmd()
    const start = dateOnly(ipoData.subscriptionStartDate)
    const end = dateOnly(ipoData.subscriptionEndDate)
    const listing = dateOnly(ipoData.listingDate)

    if (today < start) return { label: '청약 예정', className: styles.statusScheduled }
    if (today >= start && today <= end)
      return { label: '청약 진행 중', className: styles.statusInProgress }
    if (today > end && (!listing || today < listing))
      return { label: '청약 마감', className: styles.statusClosed }
    if (listing && today === listing) return { label: '상장일', className: styles.statusListed }
    if (listing && today > listing) return { label: '상장 완료', className: styles.statusListed }
    return { label: '알 수 없음', className: styles.statusUnknown }
  }

  const status = getStatus(ipo)
  const underwriterNames = [
    ...new Set((insight?.underwriters ?? []).map(({ name }) => name.trim()).filter(Boolean)),
  ]

  // 청약 가능 여부: 청약 예정·진행 중인 경우에만 보이게 함
  const canRegister = status.label === '청약 예정' || status.label === '청약 진행 중'

  // 상단 우측 CTA 영역 (청약/매도 등록)
  const registerCTA = canRegister
    ? isGuest
      ? {
          text: '로그인하고 청약 등록하기',
          href: '/login',
          state: { from: `/subscriptions/register?ipoId=${id}` },
        }
      : { text: '청약 등록하기', href: `/subscriptions/register?ipoId=${id}` }
    : null

  const saleCTA =
    isValidId && id > 0
      ? isGuest
        ? {
            text: '로그인하고 매도 등록하기',
            href: '/login',
            state: { from: `/sales/register?ipoId=${id}` },
          }
        : { text: '매도 등록하기', href: `/sales/register?ipoId=${id}` }
      : null

  if (isLoading) {
    return (
      <StyleScope>
        {
          <div className={styles.container}>
            <h1>공모주 상세</h1>
            <Spinner label="공모주 정보를 불러오는 중..." />
          </div>
        }
      </StyleScope>
    )
  }

  if (!isValidId || error) {
    return (
      <StyleScope>
        {
          <div className={styles.container}>
            <h1>공모주 상세</h1>
            <ErrorMessage message={!isValidId ? '공모주를 찾을 수 없습니다.' : getErrorMessage()} />
          </div>
        }
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <header className={styles.header}>
            <h1>{ipo!.ipoName}</h1>
            <div className={styles.meta}>
              <span className={styles.company}>{ipo!.companyName}</span>
              <span className={`${styles.statusChip} ${status.className}`}>{status.label}</span>
              {underwriterNames.length > 0 && (
                <span className={styles.underwriters}>주관사 {underwriterNames.join(' · ')}</span>
              )}
            </div>
            {/* 상단 우측 CTO 영역: 청약·매도 CTA */}
            <div className={styles.headerCTA} aria-label="상단 우측 작업">
              {registerCTA && (
                <Link
                  to={registerCTA.href}
                  state={registerCTA.state}
                  className={styles.primaryLink}
                >
                  {registerCTA.text}
                </Link>
              )}
              {saleCTA && (
                <Link to={saleCTA.href} state={saleCTA.state} className={styles.primaryLink}>
                  {saleCTA.text}
                </Link>
              )}
            </div>
          </header>

          <dl className={styles.detailGrid}>
            <div className={styles.detailItem}>
              <dt>종목코드</dt>
              <dd>{ipo!.stockCode ?? '-'}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>회차</dt>
              <dd>{ipo!.roundLabel ?? '-'}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>청약 시작일</dt>
              <dd>{formatDate(ipo!.subscriptionStartDate)}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>청약 종료일</dt>
              <dd>{formatDate(ipo!.subscriptionEndDate)}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>환불일</dt>
              <dd>
                {ipo!.refundDate ? (
                  <>
                    {formatDate(ipo!.refundDate)}
                    {ipo!.refundDateEstimated && ' (예상)'}
                  </>
                ) : (
                  '-'
                )}
              </dd>
            </div>
            <div className={styles.detailItem}>
              <dt>상장일</dt>
              <dd>{formatDate(ipo!.listingDate)}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>확정 공모가</dt>
              <dd>{formatPrice(ipo!.fixedPrice)}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>희망 공모가</dt>
              <dd>{formatBandPrice(ipo!.lowerBandPrice, ipo!.upperBandPrice)}</dd>
            </div>
            <div className={styles.detailItem}>
              <dt>공모 주식 수</dt>
              <dd>{formatShareCount(ipo!.offeringShareCount)}</dd>
            </div>
          </dl>

          <IpoInsightSections ipoId={id} listingDate={dateOnly(ipo!.listingDate)} />
        </div>
      }
    </StyleScope>
  )
}
