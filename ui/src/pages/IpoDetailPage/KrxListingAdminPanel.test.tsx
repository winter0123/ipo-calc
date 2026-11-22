import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import KrxListingAdminPanel from './KrxListingAdminPanel.tsx'
import { ApiError } from '@/shared/api/apiError.ts'

const api = vi.hoisted(() => ({
  previewKrxListing: vi.fn(),
  applyKrxListing: vi.fn(),
}))

vi.mock('@/entities/ipo/index.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/entities/ipo/index.ts')>()),
  previewKrxListing: api.previewKrxListing,
  applyKrxListing: api.applyKrxListing,
}))

const preview = {
  ipoId: 7,
  stockCode: '123456',
  currentListingDate: '2026-11-01',
  currentEvidenceType: 'ESTIMATED_DART',
  currentEvidenceReceipt: 'current-receipt',
  notice: {
    noticeUrl: 'https://kind.krx.co.kr/example',
    companyName: '테스트기업',
    stockCode: '123456',
    stockType: '보통주',
    reason: '유상증자',
    roundLabel: '제1회',
    issuedShares: 1000,
    issuePrice: 25000,
    listingDate: '2026-11-15',
    issueDate: '2026-11-10',
  },
  dartReceiptNo: 'dart-receipt',
  dartPaymentDate: '2026-11-10',
  dartIssuedShares: 1000,
  dartIssuePrice: 25000,
  dartListingDate: '2026-11-15',
  dartOriginalListingDate: '2026-11-15',
  stockCodeMatches: true,
  stockTypeCommon: true,
  reasonAllowed: true,
  singleMatch: true,
  issuedSharesMatch: true,
  issuePriceMatch: true,
  datesValid: true,
  expired: false,
  applyAllowed: true,
  idempotent: false,
  reasons: [],
  reviewStatus: 'APPLYABLE',
  expectedDartReceiptNo: 'dart-receipt',
  expectedDartListingDate: '2026-11-15',
  expectedStockCode: '123456',
  expectedIssuedShares: 1000,
  expectedIssuePrice: 25000,
  expectedKrxListingDate: '2026-11-15',
  expectedNoticeHash: 'notice-hash',
}

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
  render(
    <QueryClientProvider client={queryClient}>
      <KrxListingAdminPanel ipoId={7} />
    </QueryClientProvider>,
  )
  return { invalidate }
}

describe('KrxListingAdminPanel', () => {
  afterEach(() => vi.clearAllMocks())

  it('미리보기 기대값을 변형 없이 apply에 전달하고 적용 후 IPO 캐시를 무효화한다', async () => {
    const user = userEvent.setup()
    const { invalidate } = setup()
    api.previewKrxListing.mockResolvedValueOnce(preview)
    api.applyKrxListing.mockResolvedValueOnce({
      ipoId: 7,
      applied: true,
      status: 'APPLIED',
      reasons: [],
    })

    await user.type(
      screen.getByLabelText('KIND 추가상장 공고 URL'),
      'https://kind.krx.co.kr/example',
    )
    await user.click(screen.getByRole('button', { name: '미리보기' }))
    expect(await screen.findByText('DART 예정 상장일')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '상장일 적용' }))

    expect(api.applyKrxListing).toHaveBeenCalledWith(7, {
      noticeUrl: 'https://kind.krx.co.kr/example',
      expectedDartReceiptNo: 'dart-receipt',
      expectedDartListingDate: '2026-11-15',
      expectedStockCode: '123456',
      expectedIssuedShares: 1000,
      expectedIssuePrice: 25000,
      expectedKrxListingDate: '2026-11-15',
      expectedNoticeHash: 'notice-hash',
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['ipos'] })
  })

  it('review-only 또는 필수 기대값 누락이면 적용하지 않는다', async () => {
    const user = userEvent.setup()
    setup()
    api.previewKrxListing.mockResolvedValueOnce({ ...preview, applyAllowed: false })
    await user.type(
      screen.getByLabelText('KIND 추가상장 공고 URL'),
      'https://kind.krx.co.kr/example',
    )
    await user.click(screen.getByRole('button', { name: '미리보기' }))
    expect(await screen.findByText('검토 전용 결과로 적용할 수 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '상장일 적용' })).toBeDisabled()
    expect(api.applyKrxListing).not.toHaveBeenCalled()
  })

  it('URL 변경 시 이전 미리보기는 적용할 수 없고 409는 재미리보기를 안내한다', async () => {
    const user = userEvent.setup()
    setup()
    api.previewKrxListing.mockResolvedValueOnce(preview)
    await user.type(
      screen.getByLabelText('KIND 추가상장 공고 URL'),
      'https://kind.krx.co.kr/example',
    )
    await user.click(screen.getByRole('button', { name: '미리보기' }))
    await screen.findByText('KIND 상장일')
    await user.clear(screen.getByLabelText('KIND 추가상장 공고 URL'))
    await user.type(
      screen.getByLabelText('KIND 추가상장 공고 URL'),
      'https://kind.krx.co.kr/changed',
    )
    expect(screen.queryByRole('button', { name: '상장일 적용' })).not.toBeInTheDocument()

    api.previewKrxListing.mockResolvedValueOnce(preview)
    await user.clear(screen.getByLabelText('KIND 추가상장 공고 URL'))
    await user.type(
      screen.getByLabelText('KIND 추가상장 공고 URL'),
      'https://kind.krx.co.kr/example',
    )
    await user.click(screen.getByRole('button', { name: '미리보기' }))
    api.applyKrxListing.mockRejectedValueOnce(new ApiError(409, 'conflict'))
    await user.click(await screen.findByRole('button', { name: '상장일 적용' }))
    // The rejected request must not leave a stale candidate available for another write.
    expect(
      await screen.findByText('공고나 공모주 정보가 바뀌었습니다. URL을 다시 미리보기해 주세요.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '상장일 적용' })).not.toBeInTheDocument()
  })

  it('현재 URL이 바뀐 뒤 도착한 미리보기 응답은 화면에 결속하지 않는다', async () => {
    const user = userEvent.setup()
    setup()
    let resolvePreview!: (value: typeof preview) => void
    api.previewKrxListing.mockReturnValueOnce(
      new Promise((resolve) => {
        resolvePreview = resolve
      }),
    )
    const input = screen.getByLabelText('KIND 추가상장 공고 URL')
    await user.type(input, 'https://kind.krx.co.kr/old')
    await user.click(screen.getByRole('button', { name: '미리보기' }))
    await user.clear(input)
    await user.type(input, 'https://kind.krx.co.kr/new')
    await user.clear(input)
    await user.type(input, 'https://kind.krx.co.kr/old')
    resolvePreview(preview)
    await screen.findByRole('button', { name: '미리보기' })
    expect(screen.queryByText('KIND 상장일')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '상장일 적용' })).not.toBeInTheDocument()
  })
})
