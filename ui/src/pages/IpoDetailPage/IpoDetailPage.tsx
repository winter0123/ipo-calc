import { useParams, Link } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { getListingDisplay, useIpo, useIpoInsight } from '@/entities/ipo/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Spinner from '@/shared/components/Spinner/Spinner.tsx'
import ErrorMessage from '@/shared/components/ErrorMessage/ErrorMessage.tsx'
import styles, { StyleScope } from './IpoDetailPage.styles.ts'
import IpoInsightSections from './IpoInsightSections.tsx'
import { dateOnly, toSeoulYmd } from '@/shared/lib/date.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

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

  const formatShareCount = (count: number | null) => {
    if (count === null) return '-'
    return formatShares(count)
  }

  const getStatus = (ipoData: typeof ipo) => {
    if (!ipoData) return { label: '알 수 없음', className: styles.statusUnknown }
    const now = new Date()
    const today = toSeoulYmd(now)
    const start = dateOnly(ipoData.subscriptionStartDate)
    const end = dateOnly(ipoData.subscriptionEndDate)
    const listing = dateOnly(ipoData.listingDate)

    if (today < start) return { label: '청약 예정', className: styles.statusScheduled }
    if (today >= start && today <= end)
      return { label: '청약 진행 중', className: styles.statusInProgress }

    // 청약 종료 후에는 DART 예정/거래소 확정 증거를 날짜 기반 '상장 완료'보다 우선한다.
    const listingDisplay = getListingDisplay(ipoData, now)
    if (listingDisplay) {
      const className =
        listingDisplay.kind === 'confirmed'
          ? styles.statusListed
          : listingDisplay.kind === 'estimated'
            ? styles.statusEstimated
            : listingDisplay.kind === 'estimated-overdue'
              ? styles.statusOverdue
              : styles.statusUnknown
      return { label: listingDisplay.label, className }
    }

    // 증거가 없는 기존 데이터는 기존 날짜 기반 단계를 유지한다.
    if (today > end && (!listing || today < listing))
      return { label: '청약 마감', className: styles.statusClosed }
    if (listing && today === listing) return { label: '상장일', className: styles.statusListed }
    if (listing && today > listing) return { label: '상장 완료', className: styles.statusListed }
    return { label: '알 수 없음', className: styles.statusUnknown }
  }

  const status = getStatus(ipo)
  // 확정 공모가만 표시한다. 희망가 밴드(lower==upper 포함)로 추정하지 않는다.
  const confirmedPriceForDisplay =
    ipo?.fixedPrice != null && ipo.fixedPrice > 0 ? ipo.fixedPrice : null
  function underwriterAbbreviation(name: string): string {
    const trimmed = name.trim()
    if (formatSecuritiesFirmName(trimmed) === 'IBK') return 'IBK'
    if (trimmed === '아이엠증권') return 'IM'
    return trimmed.replace(/증권$/, '').slice(0, 2)
  }

  const underwriterNames = [
    ...new Set(
      (insight?.underwriters ?? [])
        .map(({ name }) => underwriterAbbreviation(formatSecuritiesFirmName(name)))
        .filter(Boolean),
    ),
  ]

  // 통합 관리(청약·매도 관리) CTA는 항상 보인다 — 청약일이 지나도 과거 기록을 수정·사후 기록할 수 있다.
  const manageCTA =
    isValidId && id > 0
      ? isGuest
        ? {
            text: '로그인하고 청약·매도 관리하기',
            href: '/login',
            state: { from: `/ipos/${id}/manage` },
          }
        : { text: '청약·매도 관리', href: `/ipos/${id}/manage` }
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
            <h1>공모주 상세정보</h1>
            <div className={styles.meta}>
              <span className={styles.company}>{ipo!.ipoName}</span>
              <span className={`${styles.statusChip} ${status.className}`}>{status.label}</span>
              {underwriterNames.length > 0 && (
                <span className={styles.underwriters}>{underwriterNames.join(' · ')}</span>
              )}
            </div>
            {/* 상단 우측 CTO 영역: 청약·매도 관리 */}
            <div className={styles.headerCTA} aria-label="상단 우측 작업">
              {manageCTA && (
                <Link to={manageCTA.href} state={manageCTA.state} className={styles.primaryLink}>
                  {manageCTA.text}
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
              <dt>청약일</dt>
              <dd>
                {formatDate(ipo!.subscriptionStartDate)} ~ {formatDate(ipo!.subscriptionEndDate)}
              </dd>
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
              <dt>공모가</dt>
              <dd>
                {confirmedPriceForDisplay != null ? (
                  <>
                    {formatPrice(confirmedPriceForDisplay)}
                    {ipo!.lowerBandPrice != null && ipo!.upperBandPrice != null ? (
                      <>
                        <span className={styles.bandLabel}>
                          (밴드 {formatWon(ipo!.lowerBandPrice)}~{formatWon(ipo!.upperBandPrice)})
                        </span>
                      </>
                    ) : null}
                  </>
                ) : ipo!.lowerBandPrice != null && ipo!.upperBandPrice != null ? (
                  <>
                    {formatWon(ipo!.lowerBandPrice)}~{formatWon(ipo!.upperBandPrice)}
                  </>
                ) : (
                  '-'
                )}
              </dd>
            </div>
            <div className={styles.detailItem}>
              <dt>공모 주식 수</dt>
              <dd>{formatShareCount(ipo!.offeringShareCount)}</dd>
            </div>
          </dl>

          <IpoInsightSections
            ipoId={id}
            listingDate={dateOnly(ipo!.listingDate)}
            lowerBandPrice={ipo!.lowerBandPrice}
            upperBandPrice={ipo!.upperBandPrice}
          />
        </div>
      }
    </StyleScope>
  )
}
