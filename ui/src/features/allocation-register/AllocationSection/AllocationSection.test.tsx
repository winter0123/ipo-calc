import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AllocationSection from './AllocationSection.tsx'
import { fill } from '@/test/fill.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const INVESTOR = {
  id: 1,
  displayName: '홍길동',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const ACCOUNT = {
  id: 10,
  investorId: 1,
  securitiesFirmId: 1,
  securitiesFirmName: '미래에셋증권',
  accountNumber: null,
  accountName: '위탁계좌',
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const IPO = {
  id: 7,
  companyName: '테스트기업',
  ipoName: '테스트IPO',
  stockCode: '123456',
  roundLabel: null,
  lowerBandPrice: 20000,
  upperBandPrice: 25000,
  fixedPrice: 25000,
  subscriptionStartDate: '2099-01-05T09:00:00Z',
  subscriptionEndDate: '2099-01-06T17:00:00Z',
  listingDate: '2099-02-01T09:00:00Z',
  refundDate: null,
  refundDateEstimated: null,
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

const NO_PRICE_IPO = { ...IPO, fixedPrice: null }

const DETAIL = {
  id: 55,
  subscribedAt: '2026-11-20T09:00:00Z',
  ipoId: 7,
  ipoName: '테스트IPO',
  ipoFixedPrice: 25000,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: 10,
  investorName: '홍길동',
  securitiesFirmName: '미래에셋증권',
  securitiesAccountId: 10,
  securitiesAccountName: '위탁계좌',
}

const RECORD = {
  id: 77,
  subscriptionId: 55,
  allocatedQuantity: 10,
  allocatedAt: '2026-11-21T00:00:00Z',
  allocatedAmount: 250000,
  note: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-11-21T00:00:00Z',
  updatedAt: '2026-11-21T00:00:00Z',
}

function allocationResponse(overrides: Record<string, unknown> = {}) {
  return { ...RECORD, ...overrides }
}

function renderSection(props: Partial<Parameters<typeof AllocationSection>[0]> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
  const onDirtyChange = vi.fn()
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <AllocationSection
        ipo={IPO}
        investor={INVESTOR}
        account={ACCOUNT}
        onSaved={onSaved}
        mode="create"
        status="ready"
        detail={DETAIL}
        record={null}
        onDirtyChange={onDirtyChange}
        {...props}
      />
    </QueryClientProvider>,
  )
  return { ...utils, onSaved, onDirtyChange }
}

function postCalls() {
  return vi
    .mocked(apiFetch)
    .mock.calls.filter(([path, options]) => path === '/allocations' && options?.method === 'POST')
}

function patchCalls() {
  return vi
    .mocked(apiFetch)
    .mock.calls.filter(
      ([path, options]) => path === '/allocations/77' && options?.method === 'PATCH',
    )
}

describe('배정 섹션', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('청약이 이미 있으면 subscriptionId를 담아 POST한다 (0주 배정도 허용)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ allocatedQuantity: 0 }))
    const { onSaved } = renderSection()

    await fill(screen.getByLabelText('배정수량'), '0')
    expect(screen.getByTestId('manage-allocation-total')).toHaveTextContent('0원')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(postCalls()).toHaveLength(1)
    const body = JSON.parse((postCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({
      allocatedQuantity: 0,
      ipoId: 7,
      investorId: 1,
      securitiesAccountId: 10,
      subscriptionId: 55,
    })
    expect(body).not.toHaveProperty('note')
  })

  it('청약이 아직 없으면 subscriptionId 없이 보내 서버가 자동 생성/재사용하게 한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ id: 78, allocatedQuantity: 5 }))
    const { onSaved } = renderSection({ detail: null })

    await fill(screen.getByLabelText('배정수량'), '5')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const body = JSON.parse((postCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({
      allocatedQuantity: 5,
      ipoId: 7,
      investorId: 1,
      securitiesAccountId: 10,
    })
    expect(body).not.toHaveProperty('subscriptionId')
  })

  it('배정 총 가격은 확정 공모가 × 배정수량으로 보여 준다', async () => {
    renderSection()

    await fill(screen.getByLabelText('배정수량'), '3')

    expect(screen.getByTestId('manage-allocation-total')).toHaveTextContent('75,000원')
  })

  it('확정 공모가가 없으면 총 가격을 계산하지 않는다', async () => {
    renderSection({ ipo: NO_PRICE_IPO })

    expect(screen.getByTestId('manage-allocation-total')).toHaveTextContent(
      '확정 공모가가 없어 자동 계산할 수 없습니다.',
    )
  })

  it('변경된 필드만 PATCH에 담는다 (비고만 변경)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ note: '메모' }))
    const { onSaved } = renderSection({ mode: 'edit', record: RECORD })

    await fill(screen.getByLabelText('비고 (선택)'), '메모')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const body = JSON.parse((patchCalls()[0][1] as RequestInit).body as string)
    expect(body).toEqual({ note: '메모' })
  })

  it('변경 사항이 없으면 API를 호출하지 않고 안내만 보여 준다', async () => {
    renderSection({ mode: 'edit', record: RECORD })

    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    expect(await screen.findByText('변경된 내용이 없습니다.')).toBeInTheDocument()
    expect(patchCalls()).toHaveLength(0)
    expect(postCalls()).toHaveLength(0)
  })

  it('409 중복 오류를 보여 주고 자동 재전송하지 않는다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(409, '이미 등록된 배정입니다.', '이미 등록된 배정입니다.'),
    )
    renderSection()

    await fill(screen.getByLabelText('배정수량'), '2')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 동일한 청약에 배정이 존재합니다.',
    )
    expect(screen.getByLabelText('배정수량')).toHaveValue(2)
    expect(postCalls()).toHaveLength(1)
  })

  it('서버 규칙 위반(400) 메시지는 그대로 보여 준다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(
        400,
        '이미 매도한 수량보다 작게 줄일 수 없습니다.',
        '이미 매도한 수량보다 작게 줄일 수 없습니다.',
      ),
    )
    renderSection({ mode: 'edit', record: RECORD })

    await fill(screen.getByLabelText('배정수량'), '1')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 매도한 수량보다 작게 줄일 수 없습니다.',
    )
  })

  it('저장 성공 후 dirty 플래그가 해제된다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ allocatedQuantity: 12 }))
    const { onDirtyChange, onSaved } = renderSection({ mode: 'edit', record: RECORD })

    await fill(screen.getByLabelText('배정수량'), '12')
    expect(onDirtyChange).toHaveBeenLastCalledWith(true)
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(false))
  })

  it('백그라운드 재조회로 record가 바뀌어도 입력 중인 값은 덮어쓰지 않는다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <AllocationSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          mode="edit"
          status="ready"
          detail={DETAIL}
          record={RECORD}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>,
    )

    await fill(screen.getByLabelText('배정수량'), '20')

    rerender(
      <QueryClientProvider client={queryClient}>
        <AllocationSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          mode="edit"
          status="ready"
          detail={DETAIL}
          record={{ ...RECORD, allocatedQuantity: 12 }}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>,
    )

    expect(screen.getByLabelText('배정수량')).toHaveValue(20)
  })

  it('onSaved가 끝날 때까지 잠금을 유지하고 완료되면 풀린다', async () => {
    let resolveSaved: (() => void) | null = null
    const onSaved = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveSaved = resolve
        }),
    )
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ allocatedQuantity: 5 }))
    renderSection({ onSaved })

    await fill(screen.getByLabelText('배정수량'), '5')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    // 저장 요청이 성공했어도 조회 갱신이 끝나기 전에는 다시 제출할 수 없다
    expect(await screen.findByRole('button', { name: '저장 중...' })).toBeDisabled()
    expect(screen.getByLabelText('배정수량')).toBeDisabled()

    resolveSaved!()
    await waitFor(() => expect(screen.getByRole('button', { name: '배정 저장' })).toBeEnabled())
    expect(postCalls()).toHaveLength(1)
  })

  it('조회 갱신에 실패하면 저장 성공 대신 갱신 실패 안내와 다시 불러오기를 보여 준다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse({ allocatedQuantity: 5 }))
    const onSaved = vi
      .fn<() => Promise<void>>()
      .mockRejectedValueOnce(new Error('invalidate 실패'))
      .mockResolvedValueOnce(undefined)
    renderSection({ onSaved })

    await fill(screen.getByLabelText('배정수량'), '5')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    // 다시 불러오기는 POST를 다시 보내지 않는다
    expect(postCalls()).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2))
    expect(postCalls()).toHaveLength(1)
  })

  it('배정 삭제 — 확인 시 DELETE를 호출하고 onSaved로 갱신한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined)
    const { onSaved } = renderSection({ mode: 'edit', record: RECORD })

    await userEvent.click(screen.getByRole('button', { name: '배정 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.some(
          ([path, options]) => path === '/allocations/77' && options?.method === 'DELETE',
        ),
    ).toBe(true)
  })

  it('다른 섹션 저장 중(saveDisabled)이면 저장·삭제 버튼이 잠기고 전송되지 않는다', async () => {
    renderSection({ saveDisabled: true })

    expect(screen.getByRole('button', { name: '배정 저장' })).toBeDisabled()
    expect(screen.getByLabelText('배정수량')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))
    expect(postCalls()).toHaveLength(0)
  })

  it('mutation 진행 여부를 onPendingChange로 알린다', async () => {
    let resolvePost: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/allocations' && options?.method === 'POST') {
        return new Promise((resolve) => {
          resolvePost = resolve
        })
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })
    const onPendingChange = vi.fn()
    renderSection({ onPendingChange })

    await fill(screen.getByLabelText('배정수량'), '5')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    await waitFor(() => expect(onPendingChange).toHaveBeenCalledWith(true))
    resolvePost(allocationResponse({ allocatedQuantity: 5 }))
    await waitFor(() => expect(onPendingChange).toHaveBeenLastCalledWith(false))
  })

  it('배정 삭제 — 매도가 있으면(HAS_CHILD_RECORDS) 서버 메시지를 그대로 보여 준다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(
        400,
        '매도가 있어 배정을 삭제할 수 없습니다.',
        '매도가 있어 배정을 삭제할 수 없습니다.',
      ),
    )
    renderSection({ mode: 'edit', record: RECORD })

    await userEvent.click(screen.getByRole('button', { name: '배정 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '매도가 있어 배정을 삭제할 수 없습니다.',
    )
  })

  it('조회 갱신 실패(hydrated 후 error)면 입력을 유지한 채 잠기고, 다시 불러오기로 재조회한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const onDirtyChange = vi.fn()
    const renderTree = (status: 'ready' | 'error') => (
      <QueryClientProvider client={queryClient}>
        <AllocationSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          mode="edit"
          status={status}
          detail={DETAIL}
          record={RECORD}
          onDirtyChange={onDirtyChange}
        />
      </QueryClientProvider>
    )
    const { rerender } = render(renderTree('ready'))
    await fill(screen.getByLabelText('배정수량'), '20')

    rerender(renderTree('error'))

    expect(
      await screen.findByText(/갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '배정 저장' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '배정 삭제' })).toBeDisabled()
    // 입력값은 유지된다
    expect(screen.getByLabelText('배정수량')).toHaveValue(20)

    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it('처음 조회가 실패하면(hydrated 이전) 다시 불러오기로 재조회한다', async () => {
    const { onSaved } = renderSection({ mode: 'edit', record: RECORD, status: 'error' })

    expect(await screen.findByText(/불러오지 못했습니다/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it('편집 원본(record)이 없는 edit 모드에서는 POST fallback 없이 저장이 잠긴다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(allocationResponse())
    renderSection({ mode: 'edit', record: null })

    expect(screen.getByRole('button', { name: '배정 저장' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))
    expect(postCalls()).toHaveLength(0)
    expect(patchCalls()).toHaveLength(0)
  })
})
