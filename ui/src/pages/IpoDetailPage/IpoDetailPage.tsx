import { useParams, Link } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Spinner from '@/shared/components/Spinner/Spinner.tsx'
import ErrorMessage from '@/shared/components/ErrorMessage/ErrorMessage.tsx'
import styles from './IpoDetailPage.module.css'
import IpoInsightSections from './IpoInsightSections.tsx'
import { toLocalYmd } from '@/shared/lib/date.ts'

export default function IpoDetailPage() {
  const { ipoId } = useParams<{ ipoId: string }>()
  const id = ipoId ? Number(ipoId) : NaN
  const isValidId = !Number.isNaN(id) && id > 0

  const { data: ipo, isLoading, error } = useIpo(isValidId ? id : undefined)
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
    return dateStr.split('T')[0]
  }

  const formatPrice = (price: number | null) => {
    if (price === null || price === 0) return '-'
    return `${price.toLocaleString()}원`
  }

  const formatBandPrice = (lower: number | null, upper: number | null) => {
    if (lower !== null && upper !== null) {
      return `${lower.toLocaleString()}~${upper.toLocaleString()}원`
    }
    if (lower !== null) {
      return `${lower.toLocaleString()}원`
    }
    if (upper !== null) {
      return `${upper.toLocaleString()}원`
    }
    return '-'
  }

  const formatShareCount = (count: number | null) => {
    if (count === null) return '-'
    return `${count.toLocaleString()}주`
  }

  const getStatus = (ipoData: typeof ipo) => {
    if (!ipoData) return { label: '알 수 없음', className: styles.statusUnknown }
    const today = toLocalYmd()
    const start = ipoData.subscriptionStartDate.split('T')[0]
    const end = ipoData.subscriptionEndDate.split('T')[0]
    const listing = ipoData.listingDate ? ipoData.listingDate.split('T')[0] : null

    if (today < start) return { label: '청약 예정', className: styles.statusScheduled }
    if (today >= start && today <= end)
      return { label: '청약 진행 중', className: styles.statusInProgress }
    if (today > end && (!listing || today < listing))
      return { label: '청약 마감', className: styles.statusClosed }
    if (listing && today >= listing) return { label: '상장 완료', className: styles.statusListed }
    return { label: '알 수 없음', className: styles.statusUnknown }
  }

  const status = getStatus(ipo)
  // 청약 기간이 끝나기 전(예정·진행 중)에만 등록 버튼을 보인다.
  const canRegister = status.label === '청약 예정' || status.label === '청약 진행 중'
  const registerPath = `/subscriptions/register?ipoId=${id}`

  if (isLoading) {
    return (
      <div className={styles.container}>
        <h1>공모주 상세</h1>
        <Spinner label="공모주 정보를 불러오는 중..." />
      </div>
    )
  }

  if (!isValidId || error) {
    return (
      <div className={styles.container}>
        <h1>공모주 상세</h1>
        <ErrorMessage message={!isValidId ? '공모주를 찾을 수 없습니다.' : getErrorMessage()} />
        <Link to="/ipo-calendar" className={styles.backLink}>
          공모주 일정으로 돌아가기
        </Link>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h1>{ipo!.ipoName}</h1>
        <div className={styles.meta}>
          <span className={styles.company}>{ipo!.companyName}</span>
          <span className={`${styles.statusChip} ${status.className}`}>{status.label}</span>
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

      <IpoInsightSections ipoId={id} />

      <nav className={styles.actions} aria-label="공모주 상세 액션">
        <Link to="/ipo-calendar" className={styles.secondaryLink}>
          공모주 일정으로 돌아가기
        </Link>
        {canRegister &&
          (isGuest ? (
            <Link to="/login" state={{ from: registerPath }} className={styles.primaryLink}>
              로그인하고 청약 등록하기
            </Link>
          ) : (
            <Link to={registerPath} className={styles.primaryLink}>
              청약 등록하기
            </Link>
          ))}
      </nav>
    </div>
  )
}
