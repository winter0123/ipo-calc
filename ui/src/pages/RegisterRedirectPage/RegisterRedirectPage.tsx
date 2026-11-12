import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import {
  getSubscription,
  investmentHistoryKeys,
  type Subscription,
} from '@/entities/subscription/index.ts'
import Spinner from '@/shared/components/Spinner/Spinner.tsx'
import ErrorMessage from '@/shared/components/ErrorMessage/ErrorMessage.tsx'
import { ApiError, toErrorMessage } from '@/shared/api/apiError.ts'
import styles, { StyleScope } from './RegisterRedirectPage.styles.ts'

/**
 * 예전 등록 링크(/subscriptions/register, /allocations/register, /sales/register)의 호환 리다이렉트.
 *
 * - ipoId 또는 subscriptionId 문맥이 있으면 /ipos/:ipoId/manage로 replace 이동한다(state.from 보존).
 * - subscriptionId는 권한 확인된 원본(GET /subscriptions/{id})으로 IPO/투자자/계좌를 정확히 매핑한다.
 *   현재 선택된 투자자가 그 기록의 투자자와 다르면 글로벌 투자자를 임의로 바꾸지 않고
 *   전환 안내만 보여 준다(버튼을 누른 경우에만 전환한다).
 * - 문맥이 없으면 목록 안내, 잘못된 id면 403/404 안내를 보여 준다.
 */
type IdResult = { kind: 'absent' } | { kind: 'invalid' } | { kind: 'valid'; value: number }

function parseIdParam(raw: string | null): IdResult {
  if (raw === null) return { kind: 'absent' }
  const parsed = Number(raw)
  if (!Number.isInteger(parsed) || parsed <= 0) return { kind: 'invalid' }
  return { kind: 'valid', value: parsed }
}

export default function RegisterRedirectPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const locationState = location.state ?? undefined
  const [searchParams] = useSearchParams()
  const ipoIdParam = parseIdParam(searchParams.get('ipoId'))
  const subscriptionIdParam = parseIdParam(searchParams.get('subscriptionId'))
  const { current, investors, isLoading: investorsLoading, select } = useCurrentInvestor()

  // subscriptionId 문맥일 때만 원본을 조회한다(소유권 검증 — 타인·없는 기록은 403/404).
  const sourceId = subscriptionIdParam.kind === 'valid' ? subscriptionIdParam.value : null
  const subscriptionQuery = useQuery({
    queryKey: investmentHistoryKeys.source('subscription', sourceId ?? -1),
    queryFn: ({ signal }) => getSubscription(sourceId!, signal),
    enabled: sourceId != null,
  })
  const record: Subscription | undefined = subscriptionQuery.data

  // 두 문맥이 모두 있고 서로 다르면 어느 쪽도 임의로 고르지 않고 안내만 한다.
  const mismatch =
    ipoIdParam.kind === 'valid' && record != null && record.ipoId !== ipoIdParam.value

  const resolvedIpoId = ipoIdParam.kind === 'valid' ? ipoIdParam.value : (record?.ipoId ?? null)

  // 잘못된 id — 403/404 안내에 해당한다.
  if (ipoIdParam.kind === 'invalid' || subscriptionIdParam.kind === 'invalid') {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리로 이동</h1>
          <ErrorMessage message="링크의 주소가 올바르지 않습니다. 청약·매도 관리는 공모주를 고른 뒤 열립니다." />
          <p>
            <Link to="/ipo-calendar">공모주 일정으로 이동</Link>
          </p>
        </div>
      </StyleScope>
    )
  }

  // subscriptionId 원본 조회 오류 — 타인/없는 기록은 403/404, 그 외는 일시 오류.
  if (sourceId != null && subscriptionQuery.isError) {
    const status =
      subscriptionQuery.error instanceof ApiError ? subscriptionQuery.error.status : null
    const message =
      status === 403 || status === 404
        ? '이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다.'
        : toErrorMessage(subscriptionQuery.error, {
            fallback: '청약 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
          })
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리로 이동</h1>
          <ErrorMessage message={message} />
          <p>
            <Link to="/investments">청약내역으로 이동</Link>
          </p>
        </div>
      </StyleScope>
    )
  }

  // subscriptionId 원본 조회 중
  if (sourceId != null && !subscriptionQuery.isSuccess) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리로 이동</h1>
          <Spinner label="청약 정보를 불러오는 중..." />
        </div>
      </StyleScope>
    )
  }

  // ipoId만 있는 문맥 — 통합 관리 화면으로 바로 이동한다.
  if (subscriptionIdParam.kind === 'absent' && resolvedIpoId != null) {
    return <Navigate to={`/ipos/${resolvedIpoId}/manage`} replace state={locationState} />
  }

  // 두 문맥이 서로 다르면 안내만 한다.
  if (mismatch) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리로 이동</h1>
          <ErrorMessage message="링크의 공모주와 청약 정보가 서로 다릅니다. 청약내역에서 기록을 다시 열어 주세요." />
          <p>
            <Link to="/investments">청약내역으로 이동</Link>
          </p>
        </div>
      </StyleScope>
    )
  }

  // 여기부터는 subscriptionId 문맥으로 기록이 확인된 상태다.
  if (record != null && resolvedIpoId != null) {
    if (investorsLoading) {
      return (
        <StyleScope>
          <div className={styles.container}>
            <h1 className={styles.title}>청약·매도 관리로 이동</h1>
            <Spinner label="투자자 정보를 불러오는 중..." />
          </div>
        </StyleScope>
      )
    }

    // 투자자가 한 명도 없으면 안내만 한다.
    if (investors.length === 0) {
      return (
        <StyleScope>
          <div className={styles.container}>
            <h1 className={styles.title}>청약·매도 관리로 이동</h1>
            <ErrorMessage message="먼저 투자자를 등록해 주세요." />
            <p>
              <Link to="/investors/register">투자자 등록하기</Link>
            </p>
          </div>
        </StyleScope>
      )
    }

    // 기록의 투자자가 현재 선택된 투자자와 다르면 글로벌 투자자를 임의로 바꾸지 않고 안내만 한다.
    if (current != null && record.investorId !== current.id) {
      const ownerName =
        investors.find((investor) => investor.id === record.investorId)?.displayName ??
        '다른 투자자'
      return (
        <StyleScope>
          <div className={styles.container}>
            <h1 className={styles.title}>청약·매도 관리로 이동</h1>
            <div className={styles.guide} role="status">
              <p>
                이 기록은 {ownerName}님의 청약·배정·매도 기록입니다. 다른 투자자의 기록은 이
                화면에서 열 수 없습니다.
              </p>
              <p>상단에서 투자자를 전환하면 이어서 관리할 수 있습니다.</p>
              <div className={styles.actions}>
                <button
                  type="button"
                  onClick={() => {
                    select(record.investorId)
                    // 사용자가 직접 누른 경우에만 이동한다(임의 전환 금지).
                    navigate(
                      `/ipos/${record.ipoId}/manage?securitiesAccountId=${record.securitiesAccountId}`,
                      {
                        replace: true,
                        state: locationState,
                      },
                    )
                  }}
                >
                  {ownerName}님으로 전환하고 열기
                </button>
                <Link to="/investments">청약내역으로 이동</Link>
              </div>
            </div>
          </div>
        </StyleScope>
      )
    }

    // 같은 투자자(또는 단일 투자자)라면 통합 관리 화면으로 이동한다.
    // 청약에 연결된 계좌를 문맥으로 전달한다 — TradeManagePage가 소유·주관사 계좌와 대조한 뒤에만 선택한다.
    return (
      <Navigate
        to={`/ipos/${resolvedIpoId}/manage?securitiesAccountId=${record.securitiesAccountId}`}
        replace
        state={locationState}
      />
    )
  }

  // 투자자 미선택 등으로 확정할 수 없는 경우 — 안내만 한다(임의 진행 금지).
  if (investorsLoading) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리로 이동</h1>
          <Spinner label="투자자 정보를 불러오는 중..." />
        </div>
      </StyleScope>
    )
  }

  // 문맥이 없으면 목록 안내(기존 등록 화면의 안내와 같은 역할).
  return (
    <StyleScope>
      <div className={styles.container}>
        <h1 className={styles.title}>매도·청약 등록은 공모주를 고른 뒤 시작합니다</h1>
        <div className={styles.guide}>
          <p>
            공모주 청약 목록에서 공모주를 누르고 그 화면의 <strong>청약·매도 관리</strong>를 열어
            주세요. 청약·배정·매도는 공모주와 증권계좌만으로 자동으로 연결됩니다.
          </p>
          <div className={styles.actions}>
            <Link to="/subscriptions">공모주 청약 목록으로 이동</Link>
          </div>
        </div>
      </div>
    </StyleScope>
  )
}
