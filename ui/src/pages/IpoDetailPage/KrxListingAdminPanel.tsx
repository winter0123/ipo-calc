import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, toErrorMessage } from '@/shared/api/apiError.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import { applyKrxListing, getKrxAutoStatus, ipoKeys, previewKrxListing } from '@/entities/ipo/index.ts'
import type { KrxListingPreview } from '@/entities/ipo/index.ts'
import styles from './IpoDetailPage.styles.ts'

function required(value: string | number | null) {
  return value !== null && value !== ''
}

function show(value: string | number | null | undefined) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

export default function KrxListingAdminPanel({ ipoId }: { ipoId: number }) {
  const queryClient = useQueryClient()
  const autoStatus = useQuery({
    queryKey: ['ipo', 'krx-auto-status'],
    queryFn: ({ signal }) => getKrxAutoStatus(signal),
    staleTime: 10_000,
    refetchInterval: 60_000,
  })
  const matchingAutoRow = autoStatus.data?.lastReport?.rows.find((row) => row.ipoId === ipoId)
  const [noticeUrl, setNoticeUrl] = useState('')
  const [preview, setPreview] = useState<{ key: string; data: KrxListingPreview } | null>(null)
  const [result, setResult] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const requestKey = `${ipoId}:${noticeUrl.trim()}`
  const currentKey = useRef(requestKey)
  const requestGeneration = useRef(0)

  const previewMutation = useMutation({
    mutationFn: ({ id, url }: { id: number; url: string }) => previewKrxListing(id, url),
  })
  const applyMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Parameters<typeof applyKrxListing>[1] }) =>
      applyKrxListing(id, data),
    onSuccess: (data) => {
      setResult(data.status)
      if (data.status === 'APPLIED' || data.status === 'NO_OP') {
        void queryClient.invalidateQueries({ queryKey: ipoKeys.all })
      }
    },
  })

  const displayedPreview = preview?.key === requestKey ? preview.data : null
  const expected = displayedPreview
    ? [
        displayedPreview.expectedDartReceiptNo,
        displayedPreview.expectedDartListingDate,
        displayedPreview.expectedStockCode,
        displayedPreview.expectedIssuedShares,
        displayedPreview.expectedIssuePrice,
        displayedPreview.expectedKrxListingDate,
        displayedPreview.expectedNoticeHash,
      ].every(required)
    : false
  const canApply = Boolean(displayedPreview?.applyAllowed && expected)
  const busy = previewMutation.isPending || applyMutation.isPending

  const handlePreview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const url = noticeUrl.trim()
    if (!url || busy) return
    const key = `${ipoId}:${url}`
    const generation = requestGeneration.current
    currentKey.current = key
    setPreview(null)
    setResult(null)
    setErrorMessage(null)
    try {
      const data = await previewMutation.mutateAsync({ id: ipoId, url })
      if (currentKey.current === key && requestGeneration.current === generation) {
        setPreview({ key, data })
      }
    } catch (error) {
      if (currentKey.current === key && requestGeneration.current === generation) {
        setErrorMessage(toErrorMessage(error, { fallback: '미리보기를 불러오지 못했습니다.' }))
      }
    }
  }

  const handleApply = async () => {
    if (!displayedPreview || !canApply || busy) return
    const key = requestKey
    const data = displayedPreview
    setErrorMessage(null)
    try {
      await applyMutation.mutateAsync({
        id: ipoId,
        data: {
          noticeUrl: noticeUrl.trim(),
          expectedDartReceiptNo: data.expectedDartReceiptNo!,
          expectedDartListingDate: data.expectedDartListingDate!,
          expectedStockCode: data.expectedStockCode!,
          expectedIssuedShares: data.expectedIssuedShares!,
          expectedIssuePrice: data.expectedIssuePrice!,
          expectedKrxListingDate: data.expectedKrxListingDate!,
          expectedNoticeHash: data.expectedNoticeHash!,
        },
      })
      if (currentKey.current !== key) setPreview(null)
    } catch (error) {
      if (currentKey.current === key) {
        if (error instanceof ApiError && error.status === 409) setPreview(null)
        setErrorMessage(
          toErrorMessage(error, {
            byStatus: { 409: '공고나 공모주 정보가 바뀌었습니다. URL을 다시 미리보기해 주세요.' },
            fallback: '요청을 처리하지 못했습니다.',
          }),
        )
      }
    }
  }

  return (
    <section className={styles.krxAdminPanel} aria-labelledby="krx-listing-title">
      <h2 className={styles.krxAdminTitle} id="krx-listing-title">
        관리자 · KIND 상장일 검토
      </h2>
      <div className={styles.krxAdminResult} aria-label="KIND 자동 처리 결과">
        <p>
          자동 수집 상태:{' '}
          {autoStatus.isPending
            ? '조회 중'
            : autoStatus.isError
              ? '결과 조회 실패'
              : autoStatus.data.autoApplyEnabled
                ? '자동 반영 사용'
                : '읽기 전용 검증 중'}
        </p>
        {autoStatus.data?.lastReport && (
          <>
            <p>마지막 점검: {show(autoStatus.data.lastReport.completedAt)}</p>
            <p>
              신규 확정 {autoStatus.data.lastReport.counts.NEW_CONFIRMED ?? 0}건 / 날짜 정정{' '}
              {autoStatus.data.lastReport.counts.DATE_CORRECTED ?? 0}건 / 이미 반영{' '}
              {autoStatus.data.lastReport.counts.ALREADY_APPLIED ?? 0}건 / 공고 없음{' '}
              {autoStatus.data.lastReport.counts.NO_NOTICE ?? 0}건 / 회차 충돌{' '}
              {autoStatus.data.lastReport.counts.ROUND_CONFLICT ?? 0}건 / 조회 실패{' '}
              {autoStatus.data.lastReport.counts.FETCH_FAILED ?? 0}건
            </p>
            {matchingAutoRow && (
              <p>
                이 종목: {matchingAutoRow.status}
                {matchingAutoRow.proposedListingDate
                  ? ' · 상장일 ' + matchingAutoRow.proposedListingDate
                  : ''}
                {' · ' + matchingAutoRow.reason}
              </p>
            )}
          </>
        )}
      </div>
      <form className={styles.krxAdminForm} onSubmit={handlePreview}>
        <label htmlFor="krx-notice-url">KIND 추가상장 공고 URL</label>
        <input
          className={styles.krxAdminInput}
          id="krx-notice-url"
          type="url"
          value={noticeUrl}
          disabled={applyMutation.isPending}
          onChange={(event) => {
            setNoticeUrl(event.target.value)
            setPreview(null)
            setResult(null)
            setErrorMessage(null)
            requestGeneration.current += 1
            currentKey.current = `${ipoId}:${event.target.value.trim()}`
          }}
          required
        />
        <button
          className={styles.krxAdminButton}
          type="submit"
          disabled={busy || !noticeUrl.trim()}
        >
          {previewMutation.isPending ? '미리보기 중…' : '미리보기'}
        </button>
      </form>
      {errorMessage && <p role="alert">{errorMessage}</p>}
      {result && <p role="status">적용 결과: {result}</p>}
      {displayedPreview && (
        <div className={styles.krxAdminResult} aria-live="polite">
          <dl className={styles.krxAdminDetails}>
            <dt>종목코드</dt>
            <dd>
              {show(displayedPreview.stockCode)} / KIND {show(displayedPreview.notice?.stockCode)}
            </dd>
            <dt>현재 상장일</dt>
            <dd>
              {show(displayedPreview.currentListingDate)} (
              {show(displayedPreview.currentEvidenceType)})
            </dd>
            <dt>기존 근거 접수번호</dt>
            <dd>{show(displayedPreview.currentEvidenceReceipt)}</dd>
            <dt>DART 예정 상장일</dt>
            <dd>
              {show(displayedPreview.dartListingDate)} (원래{' '}
              {show(displayedPreview.dartOriginalListingDate)})
            </dd>
            <dt>DART 납입일</dt>
            <dd>{show(displayedPreview.dartPaymentDate)}</dd>
            <dt>KIND 상장일</dt>
            <dd>{show(displayedPreview.notice?.listingDate)}</dd>
            <dt>KIND 발행일</dt>
            <dd>{show(displayedPreview.notice?.issueDate)}</dd>
            <dt>DART 접수번호</dt>
            <dd>{show(displayedPreview.dartReceiptNo)}</dd>
            <dt>발행수량</dt>
            <dd>
              {displayedPreview.notice?.issuedShares == null
                ? '-'
                : formatShares(displayedPreview.notice.issuedShares)}{' '}
              (DART{' '}
              {displayedPreview.dartIssuedShares == null
                ? '-'
                : formatShares(displayedPreview.dartIssuedShares)}
              )
            </dd>
            <dt>발행가</dt>
            <dd>
              {displayedPreview.notice?.issuePrice == null
                ? '-'
                : formatWon(displayedPreview.notice.issuePrice)}{' '}
              (DART{' '}
              {displayedPreview.dartIssuePrice == null
                ? '-'
                : formatWon(displayedPreview.dartIssuePrice)}
              )
            </dd>
            <dt>검토 사유</dt>
            <dd>{displayedPreview.notice?.reason ?? '-'}</dd>
            <dt>검토 상태</dt>
            <dd>{displayedPreview.reviewStatus}</dd>
          </dl>
          {displayedPreview.reasons.length > 0 && (
            <ul>
              {displayedPreview.reasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          )}
          <button
            className={styles.krxAdminButton}
            type="button"
            onClick={handleApply}
            disabled={!canApply || busy}
          >
            {applyMutation.isPending ? '적용 중…' : '상장일 적용'}
          </button>
          {!displayedPreview.applyAllowed && <p>검토 전용 결과로 적용할 수 없습니다.</p>}
          {displayedPreview.applyAllowed && !expected && (
            <p>적용에 필요한 기대값이 없어 요청을 보낼 수 없습니다.</p>
          )}
        </div>
      )}
    </section>
  )
}
