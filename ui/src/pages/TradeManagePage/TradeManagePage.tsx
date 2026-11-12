import { useState, type ChangeEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient, useQueries } from '@tanstack/react-query'
import { useIpo, useIpoInsight } from '@/entities/ipo/index.ts'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import {
  useSecuritiesAccounts,
  type SecuritiesAccount,
} from '@/entities/securities-account/index.ts'
import { getAllocation, type Allocation } from '@/entities/allocation/index.ts'
import {
  getSubscription,
  getSubscriptionDetail,
  getInvestmentHistory,
  investmentHistoryKeys,
  useInvestmentHistory,
  useInvestmentHistoryDetail,
  type InvestmentHistoryItem,
  type Subscription,
} from '@/entities/subscription/index.ts'
import { getSale, type Sale } from '@/entities/sale/index.ts'
import { profitKeys } from '@/entities/profit/index.ts'
import { dashboardKeys } from '@/entities/dashboard/index.ts'
import {
  SubscriptionSection,
  type SubscriptionSectionProps,
} from '@/features/subscription-register/index.ts'
import {
  AllocationSection,
  type AllocationSectionProps,
} from '@/features/allocation-register/index.ts'
import { SaleSection, type SaleSectionProps } from '@/features/sale-register/index.ts'
import { SurfaceCard, SectionHeading, StatusPill } from '@/shared/components/Primitives.tsx'
import Spinner from '@/shared/components/Spinner/Spinner.tsx'
import ErrorMessage from '@/shared/components/ErrorMessage/ErrorMessage.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { dateOnly } from '@/shared/lib/date.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { useRegisterUnsavedGuard } from '@/shared/navigation/unsavedGuard.ts'
import styles, { StyleScope } from './TradeManagePage.styles.ts'
import { EMPTY_UNDERWRITERS, accountOptionLabel, isUnderwriterAccount } from './underwriters.ts'
import type { TradeManageSectionId, SectionStatus } from './sections.ts'

const EMPTY_SALES: Sale[] = []

/** 헤더의 공모가는 확정 공모가만 표시한다. 희망가 밴드·상장일로 추정하지 않는다. */
function confirmedPriceForDisplay(fixedPrice: number | null): number | null {
  return fixedPrice != null && fixedPrice > 0 ? fixedPrice : null
}

function formatDate(value: string | null): string {
  if (!value) return '-'
  return dateOnly(value) ?? '-'
}

export default function TradeManagePage() {
  const { ipoId } = useParams<{ ipoId: string }>()
  const id = ipoId ? Number(ipoId) : NaN
  const isValidId = Number.isInteger(id) && id > 0
  const { current, isLoading: investorsLoading } = useCurrentInvestor()

  if (!isValidId) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <ErrorMessage message="공모주 주소가 올바르지 않습니다. 공모주 일정에서 다시 열어 주세요." />
          <p className={styles.hint}>
            <Link to="/ipo-calendar">공모주 일정으로 이동</Link>
          </p>
        </div>
      </StyleScope>
    )
  }

  if (investorsLoading) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <Spinner label="투자자 정보를 불러오는 중..." />
        </div>
      </StyleScope>
    )
  }

  return <TradeManageContent key={`${id}:${current?.id ?? 'none'}`} ipoId={id} />
}

function TradeManageContent({ ipoId }: { ipoId: number }) {
  const queryClient = useQueryClient()
  const { current, isLoading: investorsLoading } = useCurrentInvestor()
  const [chosenAccountId, setChosenAccountId] = useState<number | null>(null)
  const [searchParams] = useSearchParams()
  const hasRequestedAccount = searchParams.has('securitiesAccountId')
  const requestedAccountId = (() => {
    const raw = searchParams.get('securitiesAccountId')
    if (raw === null) return null
    const parsed = Number(raw)
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null
  })()
  const [dirtySections, setDirtySections] = useState<ReadonlySet<TradeManageSectionId>>(new Set())
  const [pendingAccountId, setPendingAccountId] = useState<number | null>(null)
  // 섹션별하나씩 저장 — 한 섹션의 저장/삭제 mutation이 진행되는 동안 다른 섹션의 저장을 막는다.
  const [pendingSection, setPendingSection] = useState<TradeManageSectionId | null>(null)

  const ipoQuery = useIpo(ipoId)
  const insightQuery = useIpoInsight(ipoId)
  const accountsQuery = useSecuritiesAccounts(current?.id)

  const insight = insightQuery.data
  const resolvedUnderwriters = insight?.underwriters ?? EMPTY_UNDERWRITERS
  const underwritersReady =
    !insightQuery.isLoading && !insightQuery.isError && resolvedUnderwriters.length > 0

  const allAccounts = accountsQuery.data ?? []
  const underwriterAccounts: SecuritiesAccount[] = underwritersReady
    ? allAccounts.filter((account) =>
        isUnderwriterAccount(resolvedUnderwriters, account.securitiesFirmName),
      )
    : []

  // 1개면 자동선택, 여러 개면 사용자가 구분해 고른다. 임의 fallback은 없다.
  // 외부 문맥(예전 등록 링크)으로 전달된 계좌는 소유·주관사 계좌 목록과 대조한 뒤에만 선택한다.
  // 알 수 없는 id나 현재 투자자 소유가 아닌 계좌는 임의로 고르지 않는다(자동 fallback 없음).
  const requestedValidAccountId =
    requestedAccountId !== null &&
    underwriterAccounts.some((account) => account.id === requestedAccountId)
      ? requestedAccountId
      : null
  // 링크가 계좌를 지시했지만 현재 투자자 소유·주관사 허용 계좌가 아니면,
  // 다른 계좌를 조용히 고르지 않고 사용자가 직접 고르게 한다.
  const requestedAccountInvalid = hasRequestedAccount && requestedValidAccountId === null
  const effectiveChosenAccountId = chosenAccountId ?? requestedValidAccountId
  const validChosenId =
    effectiveChosenAccountId != null &&
    underwriterAccounts.some((a) => a.id === effectiveChosenAccountId)
      ? effectiveChosenAccountId
      : null
  const autoSelected =
    !requestedAccountInvalid && underwriterAccounts.length === 1
      ? underwriterAccounts[0]
      : undefined
  const selectedAccount =
    validChosenId != null ? underwriterAccounts.find((a) => a.id === validChosenId) : autoSelected
  const selectedAccountId = selectedAccount?.id ?? null

  const investorId = current?.id
  const contextReady =
    ipoQuery.data != null && investorId != null && selectedAccountId != null && !investorsLoading

  // 투자 내역은 정확 필터(investorId+ipoId+securitiesAccountId) size:1로만 찾는다.
  const historyFilters = {
    investorId,
    ipoId,
    securitiesAccountId: selectedAccountId ?? undefined,
    size: 1,
  }
  const historyQuery = useInvestmentHistory(historyFilters, { enabled: contextReady })
  const historyItem = historyQuery.data?.content[0] ?? null
  const unexpectedHistory =
    historyQuery.data != null &&
    ((historyQuery.data.totalElements ?? 0) > 1 ||
      (historyItem != null && historyItem.subscriptionStatus !== 'ACTIVE'))
  const historyBroken = historyQuery.isError || unexpectedHistory
  const activeItem = historyItem?.subscriptionStatus === 'ACTIVE' ? historyItem : null
  const subscriptionId = activeItem?.id ?? null

  const detailQuery = useInvestmentHistoryDetail(subscriptionId)
  const detail = detailQuery.data ?? null

  // 편집 원본(개별 GET) — summary에 없는 depositAmount/memo/note/price의 유일한 출처다.
  const subscriptionSourceQuery = useQuery({
    queryKey: investmentHistoryKeys.source('subscription', subscriptionId ?? -1),
    queryFn: ({ signal }) => getSubscription(subscriptionId!, signal),
    enabled: subscriptionId != null,
  })
  const subscriptionSource: Subscription | null = subscriptionSourceQuery.data ?? null

  const allocationId = detail?.allocation?.id ?? null
  const allocationSourceQuery = useQuery({
    queryKey: investmentHistoryKeys.source('allocation', allocationId ?? -1),
    queryFn: ({ signal }) => getAllocation(allocationId!, signal),
    enabled: allocationId != null,
  })
  const allocationSource: Allocation | null = allocationSourceQuery.data ?? null

  const saleIds = detail?.sales.map((sale) => sale.id) ?? []
  const saleSourceQueries = useQueries({
    queries: saleIds.map((saleId) => ({
      queryKey: investmentHistoryKeys.source('sale', saleId),
      queryFn: ({ signal }: { signal: AbortSignal }) => getSale(saleId, signal),
    })),
  })
  const salesLoaded = saleSourceQueries.every((result) => result.isSuccess)
  const saleSources: Sale[] | null =
    saleIds.length === 0
      ? EMPTY_SALES
      : salesLoaded
        ? saleSourceQueries.flatMap((result) => (result.data ? [result.data] : []))
        : null

  // 전역 미저장/진행 중 보호 — 이 페이지는 여기서 상태만 신고하고, 경고/차단은 앱 공통이 맡는다.
  // dirty면 이동 시 확인, pending(저장·필수 재조회 진행)이면 이동 자체를 막는다.
  useRegisterUnsavedGuard('trade-manage-page', {
    dirty: dirtySections.size > 0,
    pending: pendingSection !== null,
  })

  const handleSectionDirtyChange = (section: TradeManageSectionId, dirty: boolean) => {
    setDirtySections((prev) => {
      const next = new Set(prev)
      if (dirty) next.add(section)
      else next.delete(section)
      return next
    })
  }

  const handleSectionPendingChange = (section: TradeManageSectionId, pending: boolean) => {
    setPendingSection((prev) => {
      if (pending) return section
      return prev === section ? null : prev
    })
  }

  /**
   * 저장/삭제 성공 후 투자 내역을 최신 상태로 만든다.
   * - 필수 재조회는 항상 "목록 → 상세 → 원본" 순서다.
   *   저장으로 새로 생성된 청약·배정·매도 id는 이전 시점의 id 목록으로는 알 수 없으므로,
   *   목록(정확 필터 size:1, ACTIVE 검증) → 그 id의 상세 → 상세 기준 편집 원본 순으로
   *   실제로 조회한다. 신규 원본 GET이 지연/실패해도 호출부가 갱신 실패로 처리한다.
   * - 최신 목록/상세에서 아직 ACTIVE로 확인된 원본의 404는 성공으로 삼키지 않는다.
   * - 최신 목록/상세에서 빠진(삭제 확인된) 이전 id는 재조회하지 않는다.
   * - 수익·대시보드(부가) 실패는 투자 내역 동기화 성공 판정을 뒤집지 않는다.
   */
  const invalidateAll = async () => {
    // 전체를 stale로 표시만 하고(비활성 쿼리도 다음 진입 때 재조회), 필수 재조회만 직접 한다.
    await queryClient.invalidateQueries({
      queryKey: investmentHistoryKeys.all,
      refetchType: 'none',
    })
    // 1) 목록 — 정확 필터 size:1로 현재 ACTIVE 기록을 확인한다(연결 조회 계약과 동일).
    let latestItem: InvestmentHistoryItem | null = null
    try {
      const list = await queryClient.fetchQuery({
        queryKey: investmentHistoryKeys.list(historyFilters),
        queryFn: ({ signal }) => getInvestmentHistory(historyFilters, signal),
      })
      const activeItems = (list.content ?? []).filter(
        (item) => item.subscriptionStatus === 'ACTIVE',
      )
      if ((list.totalElements ?? 0) > 1 || activeItems.length > 1) {
        // 다중 ACTIVE/비활성 유입 — 목록 자체가 일관되지 않으므로 동기화 실패로 본다.
        throw new Error('같은 조건의 청약이 2건 이상 조회되어 동기화할 수 없습니다.')
      }
      latestItem = activeItems[0] ?? null
    } catch {
      throw new Error('투자 내역을 최신 상태로 갱신하지 못했습니다.')
    }
    // 2) 상세 — 목록의 ACTIVE id로 현재 상세를 읽는다(배정·매도 id의 유일한 출처).
    let latestDetail: InvestmentHistoryItem | null = null
    if (latestItem) {
      try {
        latestDetail = await queryClient.fetchQuery({
          queryKey: investmentHistoryKeys.detail(latestItem.id),
          queryFn: ({ signal }) => getSubscriptionDetail(latestItem.id, signal),
        })
      } catch {
        throw new Error('투자 내역을 최신 상태로 갱신하지 못했습니다.')
      }
    }
    // 3) 원본 — 최신 목록/상세 기준 id만 재조회한다. 모두 ACTIVE로 확인된 기록이므로
    //    404를 포함한 어떤 실패도 갱신 실패로 본다.
    try {
      const sources: Promise<unknown>[] = []
      if (latestItem) {
        sources.push(
          queryClient.fetchQuery({
            queryKey: investmentHistoryKeys.source('subscription', latestItem.id),
            queryFn: ({ signal }) => getSubscription(latestItem.id, signal),
          }),
        )
        const latestAllocationId = latestDetail?.allocation?.id
        if (latestAllocationId != null) {
          sources.push(
            queryClient.fetchQuery({
              queryKey: investmentHistoryKeys.source('allocation', latestAllocationId),
              queryFn: ({ signal }) => getAllocation(latestAllocationId, signal),
            }),
          )
        }
        for (const sale of latestDetail?.sales ?? []) {
          sources.push(
            queryClient.fetchQuery({
              queryKey: investmentHistoryKeys.source('sale', sale.id),
              queryFn: ({ signal }) => getSale(sale.id, signal),
            }),
          )
        }
      }
      await Promise.all(sources)
    } catch {
      throw new Error('투자 내역을 최신 상태로 갱신하지 못했습니다.')
    }
    await Promise.allSettled([
      queryClient.invalidateQueries({ queryKey: profitKeys.all }),
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all }),
    ])
  }

  // 계좌 1개 자동선택인 경우, 빈 옵션을 골라도 실질 선택은 같다 — dirty 확인을 건너뛰고 조용히 덮지 않는다.
  const autoSelectableId = underwriterAccounts.length === 1 ? underwriterAccounts[0].id : null
  const effectiveAccountId = (picked: number | null) =>
    picked === null ? autoSelectableId : picked

  const handleAccountChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const raw = event.target.value
    const next = raw === '' ? null : Number(raw)
    if (effectiveAccountId(next) === selectedAccountId) return
    if (dirtySections.size > 0) {
      setPendingAccountId(next)
      return
    }
    setChosenAccountId(next)
  }

  const confirmAccountSwitch = () => {
    // 실질 변경이 없는 확인(단일 자동선택 복귀)이면 dirty 상태를 그대로 둔다.
    if (effectiveAccountId(pendingAccountId) === selectedAccountId) {
      setPendingAccountId(null)
      return
    }
    setChosenAccountId(pendingAccountId)
    setDirtySections(new Set())
    setPendingAccountId(null)
  }

  if (investorsLoading) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <Spinner label="투자자 정보를 불러오는 중..." />
        </div>
      </StyleScope>
    )
  }

  if (ipoQuery.isPending) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <Spinner label="공모주 정보를 불러오는 중..." />
        </div>
      </StyleScope>
    )
  }

  if (ipoQuery.isError || !ipoQuery.data) {
    return (
      <StyleScope>
        <div className={styles.container}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <ErrorMessage
            message={toErrorMessage(ipoQuery.error, {
              byStatus: { 404: '공모주를 찾을 수 없습니다.' },
              fallback: '공모주 정보를 불러오지 못했습니다.',
            })}
          />
          <p className={styles.hint}>
            <Link to="/ipo-calendar">공모주 일정으로 이동</Link>
          </p>
        </div>
      </StyleScope>
    )
  }

  const ipo = ipoQuery.data
  const confirmedPrice = confirmedPriceForDisplay(ipo.fixedPrice)
  const recordExists = activeItem != null

  // features는 문맥(공모주·투자자·계좌)과 onSaved를 props로만 받는다(자체 navigate·계좌 select 없음).
  const baseSectionProps = {
    ipo,
    investor: current!,
    account: selectedAccount!,
    onSaved: invalidateAll,
  }

  // 섹션 상태 — 'error'를 '기록 없음'으로 바꿔치기하지 않는다.
  // 편집 원본(GET)이 지연/실패하면 ready로 두지 않는다(신규 POST로 바꿔치기 방지).
  const subscriptionStatus: SectionStatus =
    historyQuery.isLoading ||
    detailQuery.isLoading ||
    (subscriptionId != null &&
      !subscriptionSourceQuery.isSuccess &&
      !subscriptionSourceQuery.isError)
      ? 'loading'
      : historyBroken || detailQuery.isError || subscriptionSourceQuery.isError
        ? 'error'
        : 'ready'
  const allocationStatus: SectionStatus = !contextReady
    ? 'ready'
    : historyBroken || detailQuery.isError || allocationSourceQuery.isError
      ? 'error'
      : historyQuery.isLoading ||
          detailQuery.isLoading ||
          (allocationId != null &&
            !allocationSourceQuery.isSuccess &&
            !allocationSourceQuery.isError)
        ? 'loading'
        : 'ready'
  const saleStatus: SectionStatus = !contextReady
    ? 'ready'
    : historyBroken || detailQuery.isError || saleSourceQueries.some((r) => r.isError)
      ? 'error'
      : historyQuery.isLoading ||
          detailQuery.isLoading ||
          (saleIds.length > 0 && saleSourceQueries.some((r) => r.isPending))
        ? 'loading'
        : 'ready'

  const contextReadyForSections = ipo != null && current != null && selectedAccount != null
  const subscriptionSectionProps: SubscriptionSectionProps | null = contextReadyForSections
    ? {
        ...baseSectionProps,
        mode: recordExists ? 'edit' : 'create',
        status: subscriptionStatus,
        detail,
        record: subscriptionSource,
        onDirtyChange: (dirty: boolean) => handleSectionDirtyChange('subscription', dirty),
        saveDisabled: pendingSection !== null && pendingSection !== 'subscription',
        onPendingChange: (pending: boolean) => handleSectionPendingChange('subscription', pending),
      }
    : null
  const allocationSectionProps: AllocationSectionProps | null = contextReadyForSections
    ? {
        ...baseSectionProps,
        mode: allocationId != null ? 'edit' : 'create',
        status: allocationStatus,
        detail,
        record: allocationSource,
        onDirtyChange: (dirty: boolean) => handleSectionDirtyChange('allocation', dirty),
        saveDisabled: pendingSection !== null && pendingSection !== 'allocation',
        onPendingChange: (pending: boolean) => handleSectionPendingChange('allocation', pending),
      }
    : null
  const saleSectionProps: SaleSectionProps | null = contextReadyForSections
    ? {
        ...baseSectionProps,
        status: saleStatus,
        detail,
        records: saleSources ?? EMPTY_SALES,
        onDirtyChange: (dirty: boolean) => handleSectionDirtyChange('sale', dirty),
        saveDisabled: pendingSection !== null && pendingSection !== 'sale',
        onPendingChange: (pending: boolean) => handleSectionPendingChange('sale', pending),
      }
    : null

  const accountSelectDisabled =
    !current ||
    accountsQuery.isLoading ||
    accountsQuery.isError ||
    allAccounts.length === 0 ||
    !underwritersReady ||
    // 섹션 저장/삭제와 조회 갱신이 끝날 때까지는 계좌를 바꾸지 못하게 한다.
    pendingSection !== null

  const accountSelectPlaceholder = !current
    ? '투자자를 먼저 선택해 주세요'
    : accountsQuery.isLoading
      ? '불러오는 중...'
      : insightQuery.isLoading
        ? '주관사 확인 중...'
        : insightQuery.isError
          ? '주관사 정보를 확인할 수 없습니다.'
          : resolvedUnderwriters.length === 0
            ? '등록된 주관사 정보가 없습니다.'
            : '증권계좌 선택'

  return (
    <StyleScope>
      <div className={styles.container}>
        <header className={styles.header}>
          <h1 className={styles.title}>청약·매도 관리</h1>
          <p className={styles.ipoName}>{ipo.ipoName}</p>
          <ul className={styles.meta}>
            {ipo.stockCode && (
              <li>
                종목코드 <strong>{ipo.stockCode}</strong>
              </li>
            )}
            <li>
              공모가 <strong>{confirmedPrice != null ? formatWon(confirmedPrice) : '미정'}</strong>
            </li>
            <li>
              청약일{' '}
              <strong>
                {formatDate(ipo.subscriptionStartDate)} ~ {formatDate(ipo.subscriptionEndDate)}
              </strong>
            </li>
            <li>
              현재 투자자 <strong>{current?.displayName ?? '선택 안 됨'}</strong>
            </li>
          </ul>
        </header>

        <section className={styles.field}>
          {accountsQuery.isError ? (
            <div role="alert" className={styles.error}>
              증권계좌 목록을 불러오지 못했습니다. 네트워크 상태를 확인하고 다시 시도해 주세요.
              <button
                type="button"
                className={styles.retryButton}
                onClick={() => accountsQuery.refetch()}
              >
                다시 시도
              </button>
            </div>
          ) : allAccounts.length === 0 && !accountsQuery.isLoading ? (
            <div role="status" className={styles.guide}>
              <p>등록된 증권계좌가 없습니다. 청약·매도는 증권계좌가 있어야 기록할 수 있습니다.</p>
              <Link to="/my-page">마이페이지에서 증권계좌 등록하기</Link>
            </div>
          ) : accountSelectDisabled && insightQuery.isError ? (
            <div role="alert" className={styles.error}>
              주관사 정보를 확인할 수 없어 증권계좌를 고를 수 없습니다.
              <button
                type="button"
                className={styles.retryButton}
                onClick={() => insightQuery.refetch()}
              >
                다시 시도
              </button>
            </div>
          ) : (
            <>
              <label htmlFor="securitiesAccountId">증권계좌</label>
              <select
                id="securitiesAccountId"
                value={selectedAccountId ?? ''}
                onChange={handleAccountChange}
                disabled={accountSelectDisabled}
                aria-describedby={
                  underwritersReady && underwriterAccounts.length === 0
                    ? undefined
                    : 'securitiesAccountId-hint'
                }
              >
                {selectedAccountId == null && <option value="">{accountSelectPlaceholder}</option>}
                {underwriterAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {accountOptionLabel(account)}
                  </option>
                ))}
              </select>
              {underwritersReady && underwriterAccounts.length === 0 ? (
                <div role="status" className={styles.guide}>
                  <p>
                    이 공모주의 주관사에 해당하는 증권계좌가 없습니다. 주관사 증권계좌를 먼저 등록해
                    주세요.
                  </p>
                  <Link to="/my-page">마이페이지에서 증권계좌 등록하기</Link>
                </div>
              ) : (
                <p id="securitiesAccountId-hint" className={styles.hint}>
                  주관사 증권사 계좌만 골라 보여 줍니다. 번호 없는 계좌도 선택할 수 있습니다.
                </p>
              )}
              {requestedAccountInvalid && (
                <p role="alert" className={styles.hint}>
                  링크의 계좌를 이 공모주에서 사용할 수 없습니다. 허용된 증권계좌를 직접 선택해
                  주세요.
                </p>
              )}
              {underwriterAccounts.length > 1 && selectedAccountId == null && (
                <p role="status" className={styles.hint}>
                  관리할 증권계좌를 선택해 주세요.
                </p>
              )}
            </>
          )}
        </section>

        {historyQuery.isError && (
          <div role="alert" className={styles.error}>
            이 계좌의 투자 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
            <button
              type="button"
              className={styles.retryButton}
              onClick={() => historyQuery.refetch()}
            >
              다시 시도
            </button>
          </div>
        )}
        {historyQuery.data != null && (historyQuery.data.totalElements ?? 0) > 1 && (
          <div role="alert" className={styles.error}>
            같은 조건의 청약이 2건 이상 조회되어 이 화면에서 처리할 수 없습니다. 투자 내역에서
            기록을 확인해 주세요.
          </div>
        )}
        {historyItem != null && historyItem.subscriptionStatus !== 'ACTIVE' && (
          <div role="alert" className={styles.error}>
            이 청약 기록의 상태를 확인할 수 없습니다({historyItem.subscriptionStatus}). 투자
            내역에서 상태를 확인한 뒤 다시 시도해 주세요.
          </div>
        )}

        <div className={styles.sections}>
          <SurfaceCard aria-labelledby="section-title-subscription">
            <SectionHeading id="section-title-subscription">
              청약
              {subscriptionStatus === 'loading' && (
                <StatusPill $tone="info">불러오는 중</StatusPill>
              )}
              {subscriptionStatus === 'error' && <StatusPill $tone="danger">확인 필요</StatusPill>}
            </SectionHeading>
            {subscriptionSectionProps ? (
              <SubscriptionSection {...subscriptionSectionProps} />
            ) : (
              <p className={styles.sectionBody}>
                증권계좌를 선택하면 청약을 입력하거나 불러올 수 있습니다.
              </p>
            )}
          </SurfaceCard>

          <SurfaceCard aria-labelledby="section-title-allocation">
            <SectionHeading id="section-title-allocation">
              배정
              {allocationStatus === 'loading' && <StatusPill $tone="info">불러오는 중</StatusPill>}
              {allocationStatus === 'error' && <StatusPill $tone="danger">확인 필요</StatusPill>}
            </SectionHeading>
            {allocationSectionProps ? (
              <AllocationSection {...allocationSectionProps} />
            ) : (
              <p className={styles.sectionBody}>
                증권계좌를 선택하면 배정을 입력하거나 불러올 수 있습니다.
              </p>
            )}
          </SurfaceCard>

          <SurfaceCard aria-labelledby="section-title-sale">
            <SectionHeading id="section-title-sale">
              매도
              {saleStatus === 'loading' && <StatusPill $tone="info">불러오는 중</StatusPill>}
              {saleStatus === 'error' && <StatusPill $tone="danger">확인 필요</StatusPill>}
            </SectionHeading>
            {saleSectionProps ? (
              <SaleSection {...saleSectionProps} />
            ) : (
              <p className={styles.sectionBody}>
                증권계좌를 선택하면 매도를 입력하거나 불러올 수 있습니다.
              </p>
            )}
          </SurfaceCard>
        </div>
      </div>

      <ConfirmDialog
        isOpen={pendingAccountId !== null}
        title="저장하지 않은 입력이 있습니다"
        message="다른 계좌로 바꾸면 지금 입력한 내용이 지워집니다. 그래도 바꿀까요?"
        confirmLabel="바꾸기"
        cancelLabel="계속 작성"
        onConfirm={confirmAccountSwitch}
        onCancel={() => setPendingAccountId(null)}
      />
    </StyleScope>
  )
}
