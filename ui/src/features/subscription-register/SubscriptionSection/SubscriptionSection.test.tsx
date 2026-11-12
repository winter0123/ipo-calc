import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SubscriptionSection from './SubscriptionSection.tsx'
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

// Ipo 타입상 청약 일정은 string이지만, 형식이 잘못된 값(빈 문자열 등)이 오면 기간 미정으로 처리한다.
const NO_PERIOD_IPO = {
  ...IPO,
  subscriptionStartDate: '',
  subscriptionEndDate: '',
}

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
  id: 55,
  investorId: 1,
  securitiesAccountId: 10,
  ipoId: 7,
  quantity: 10,
  depositAmount: 125000,
  subscribedAt: '2099-01-05T09:00:00Z',
  memo: null,
  status: 'ACTIVE',
  createdAt: '2026-11-20T00:00:00Z',
  updatedAt: '2026-11-20T00:00:00Z',
}

const OUT_OF_PERIOD_RECORD = {
  ...RECORD,
  subscribedAt: '2026-01-15T09:00:00Z',
}

const AUTO_GENERATED_RECORD = {
  ...RECORD,
  depositAmount: 0,
  memo: '매도/배정 등록에서 자동 생성된 청약 연결 기록',
}

function subscriptionResponse(overrides: Record<string, unknown> = {}) {
  return { ...RECORD, ...overrides }
}

function renderSection(props: Partial<Parameters<typeof SubscriptionSection>[0]> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
  const onDirtyChange = vi.fn()
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <SubscriptionSection
        ipo={IPO}
        investor={INVESTOR}
        account={ACCOUNT}
        onSaved={onSaved}
        mode="create"
        status="ready"
        detail={null}
        record={null}
        onDirtyChange={onDirtyChange}
        {...props}
      />
    </QueryClientProvider>,
  )
  return { ...utils, onSaved, onDirtyChange }
}

function patchCalls() {
  return vi
    .mocked(apiFetch)
    .mock.calls.filter(
      ([path, options]) => path === '/subscriptions/55' && options?.method === 'PATCH',
    )
}

function postCalls() {
  return vi
    .mocked(apiFetch)
    .mock.calls.filter(([path, options]) => path === '/subscriptions' && options?.method === 'POST')
}

describe('청약 섹션', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('신규 POST — 같은 페이지 유지, 실제 기간 버튼 선택값·증거금 자동계산이 payload에 담긴다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ id: 101 }))
    const { onSaved } = renderSection()

    await fill(screen.getByLabelText('청약수량'), '10')
    // 확정 공모가 × 수량 × 50% = 125,000 자동 입력
    await waitFor(() => expect(screen.getByLabelText('증거금')).toHaveValue('125,000'))
    await userEvent.click(screen.getByRole('button', { name: '2099-01-06' }))
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(postCalls()).toHaveLength(1)
    const body = JSON.parse((postCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({
      investorId: 1,
      securitiesAccountId: 10,
      ipoId: 7,
      quantity: 10,
      depositAmount: 125000,
      subscribedAt: '2099-01-06T09:00',
    })
    expect(body).not.toHaveProperty('memo')
  })

  it('배정·매도를 먼저 등록하지 않아도 청약만 저장할 수 있다(배정/매도 필수 아님)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ id: 102 }))
    const { onSaved } = renderSection()

    await fill(screen.getByLabelText('청약수량'), '3')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(postCalls()).toHaveLength(1)
  })

  it('청약 일정을 확인할 수 없으면 신규 등록을 차단하고 안내를 보여 준다', async () => {
    renderSection({ ipo: NO_PERIOD_IPO })

    expect(
      await screen.findByText(/청약 일정을 확인할 수 없어 신규 등록을 할 수 없습니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '청약 저장' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))
    expect(postCalls()).toHaveLength(0)
  })

  it('일정 미확인 기존 기록 — 다른 항목만 저장하고 청약일은 원본 보존한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse())
    const { onSaved } = renderSection({
      mode: 'edit',
      record: OUT_OF_PERIOD_RECORD,
      detail: { ...DETAIL, subscribedAt: OUT_OF_PERIOD_RECORD.subscribedAt },
      ipo: NO_PERIOD_IPO,
    })

    expect(
      await screen.findByText(/청약 일정을 확인할 수 없어 날짜를 바꿀 수 없습니다/),
    ).toBeInTheDocument()
    await fill(screen.getByLabelText('청약수량'), '20')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(patchCalls()).toHaveLength(1)
    const body = JSON.parse((patchCalls()[0][1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 20 })
  })

  it('기간 밖 기존 청약일은 날짜를 건드리지 않으면 PATCH에 subscribedAt을 보내지 않는다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse())
    const { onSaved } = renderSection({
      mode: 'edit',
      record: OUT_OF_PERIOD_RECORD,
      detail: { ...DETAIL, subscribedAt: OUT_OF_PERIOD_RECORD.subscribedAt },
    })

    expect(await screen.findByText(/청약 기간 밖이라 그대로 보존됩니다/)).toBeInTheDocument()
    await fill(screen.getByLabelText('메모 (선택)'), '기간 밖이지만 유지')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(patchCalls()).toHaveLength(1)
    const body = JSON.parse((patchCalls()[0][1] as RequestInit).body as string)
    expect(body).toEqual({ memo: '기간 밖이지만 유지' })
    expect(body).not.toHaveProperty('subscribedAt')
  })

  it('변경된 필드만 PATCH에 담는다 (수량만 변경)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ quantity: 30 }))
    const { onSaved } = renderSection({
      mode: 'edit',
      record: RECORD,
      detail: DETAIL,
    })

    await fill(screen.getByLabelText('청약수량'), '30')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    const body = JSON.parse((patchCalls()[0][1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 30 })
  })

  it('변경 사항이 없으면 API를 호출하지 않고 안내만 보여 준다', async () => {
    renderSection({ mode: 'edit', record: RECORD, detail: DETAIL })

    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    expect(await screen.findByText('변경된 내용이 없습니다.')).toBeInTheDocument()
    expect(patchCalls()).toHaveLength(0)
    expect(postCalls()).toHaveLength(0)
  })

  it('매도·배정을 먼저 등록하면 함께 만들어진 청약일 가능성 안내를 보여 주고 원본 값을 덮어쓰지 않는다', async () => {
    renderSection({ mode: 'edit', record: AUTO_GENERATED_RECORD, detail: DETAIL })

    expect(
      await screen.findByText(/매도·배정을 먼저 등록하면 청약이 함께 만들어질 수 있습니다/),
    ).toBeInTheDocument()
    // 인라인(dirty)이 아닌 원본 memo 마커는 그대로 표시된다.
    expect(screen.getByLabelText('메모 (선택)')).toHaveValue(
      '매도/배정 등록에서 자동 생성된 청약 연결 기록',
    )
    expect(screen.getByLabelText('증거금')).toHaveValue('0')
  })

  it('백그라운드 재조회로 record가 바뀌어도 입력 중인 값은 덮어쓰지 않는다', async () => {
    const { rerender, onDirtyChange } = renderSection({
      mode: 'edit',
      record: RECORD,
      detail: DETAIL,
    })

    await fill(screen.getByLabelText('청약수량'), '20')
    expect(onDirtyChange).toHaveBeenLastCalledWith(true)

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    rerender(
      <QueryClientProvider client={queryClient}>
        <SubscriptionSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={vi.fn().mockResolvedValue(undefined)}
          mode="edit"
          status="ready"
          detail={DETAIL}
          record={{ ...RECORD, quantity: 12 }}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>,
    )

    expect(screen.getByLabelText('청약수량')).toHaveValue(20)
  })

  it('409 중복 오류를 서버 메시지로 보여 주고 자동 재전송하지 않는다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(409, '이미 등록된 청약입니다.', '이미 등록된 청약입니다.'),
    )
    renderSection()

    await fill(screen.getByLabelText('청약수량'), '10')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 동일한 조건의 청약이 존재합니다.',
    )
    expect(screen.getByLabelText('청약수량')).toHaveValue(10)
    expect(postCalls()).toHaveLength(1)
  })

  it('저장 성공 후 dirty 플래그가 해제된다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ quantity: 20 }))
    const { onDirtyChange, onSaved } = renderSection({
      mode: 'edit',
      record: RECORD,
      detail: DETAIL,
    })

    await fill(screen.getByLabelText('청약수량'), '20')
    expect(onDirtyChange).toHaveBeenLastCalledWith(true)
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(false))
  })

  it('onSaved가 끝날 때까지 잠금을 유지하고 완료되면 풀린다', async () => {
    let resolveSaved: (() => void) | null = null
    const onSaved = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveSaved = resolve
        }),
    )
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ id: 55 }))
    renderSection({ onSaved })

    await fill(screen.getByLabelText('청약수량'), '10')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    // 저장 요청이 성공했어도 조회 갱신이 끝나기 전에는 다시 제출할 수 없다
    expect(await screen.findByRole('button', { name: '저장 중...' })).toBeDisabled()
    expect(screen.getByLabelText('청약수량')).toBeDisabled()

    resolveSaved!()
    await waitFor(() => expect(screen.getByRole('button', { name: '청약 저장' })).toBeEnabled())
    expect(postCalls()).toHaveLength(1)
  })

  it('조회 갱신에 실패하면 저장 성공 대신 갱신 실패 안내와 다시 불러오기를 보여 준다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse({ id: 55 }))
    const onSaved = vi
      .fn<() => Promise<void>>()
      .mockRejectedValueOnce(new Error('invalidate 실패'))
      .mockResolvedValueOnce(undefined)
    renderSection({ onSaved })

    await fill(screen.getByLabelText('청약수량'), '10')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    // 다시 불러오기는 POST를 다시 보내지 않는다
    expect(postCalls()).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2))
    expect(postCalls()).toHaveLength(1)
  })

  it('create→edit 전환(다른 섹션 저장으로 자동 생성) 뒤에도 typed 값을 유지하고 저장할 수 있다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/subscriptions/55' && method === 'PATCH')
        return subscriptionResponse({ quantity: 5, memo: 'x' })
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    const utils = renderSection({})
    const quantity = await screen.findByLabelText('청약수량')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '5')
    await fill(screen.getByLabelText('메모 (선택)'), 'x')

    // 청약 기록이 생겼다 — 폼은 입력값을 유지한 채 edit 모드가 된다
    utils.rerender(
      <QueryClientProvider
        client={
          new QueryClient({
            defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
          })
        }
      >
        <SubscriptionSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={vi.fn().mockResolvedValue(undefined)}
          mode="edit"
          status="ready"
          detail={DETAIL}
          record={{
            ...RECORD,
            quantity: 3,
            depositAmount: 0,
            subscribedAt: '2026-11-12T09:00:00Z',
            memo: '매도/배정 등록에서 자동 생성된 청약 연결 기록',
          }}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>,
    )

    expect(screen.getByLabelText('청약수량')).toHaveValue(5)
    expect(screen.getByLabelText('메모 (선택)')).toHaveValue('x')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(patchCalls()).toHaveLength(1))
    const body = JSON.parse((patchCalls()[0][1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 5, memo: 'x' })
  })

  it('청약 삭제 — 확인 시 DELETE를 호출하고 onSaved로 갱신한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined)
    const { onSaved } = renderSection({ mode: 'edit', record: RECORD, detail: DETAIL })

    await userEvent.click(screen.getByRole('button', { name: '청약 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.some(
          ([path, options]) => path === '/subscriptions/55' && options?.method === 'DELETE',
        ),
    ).toBe(true)
  })

  it('다른 섹션 저장 중(saveDisabled)이면 저장 버튼이 잠기고 전송되지 않는다', async () => {
    renderSection({ saveDisabled: true })

    expect(screen.getByRole('button', { name: '청약 저장' })).toBeDisabled()
    expect(screen.getByLabelText('청약수량')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))
    expect(postCalls()).toHaveLength(0)
  })

  it('mutation 진행 여부를 onPendingChange로 알린다', async () => {
    let resolvePost: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/subscriptions' && options?.method === 'POST') {
        return new Promise((resolve) => {
          resolvePost = resolve
        })
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })
    const onPendingChange = vi.fn()
    renderSection({ onPendingChange })

    await fill(screen.getByLabelText('청약수량'), '10')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    await waitFor(() => expect(onPendingChange).toHaveBeenCalledWith(true))
    resolvePost(subscriptionResponse({ quantity: 10 }))
    await waitFor(() => expect(onPendingChange).toHaveBeenLastCalledWith(false))
  })

  it('청약 삭제 — 배정/매도가 있으면(HAS_CHILD_RECORDS) 서버 메시지를 그대로 보여 준다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(
        400,
        '매도가 있어 청약을 삭제할 수 없습니다.',
        '매도가 있어 청약을 삭제할 수 없습니다.',
      ),
    )
    renderSection({ mode: 'edit', record: RECORD, detail: DETAIL })

    await userEvent.click(screen.getByRole('button', { name: '청약 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '매도가 있어 청약을 삭제할 수 없습니다.',
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
        <SubscriptionSection
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
    await fill(screen.getByLabelText('청약수량'), '20')

    rerender(renderTree('error'))

    expect(
      await screen.findByText(/갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '청약 저장' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '청약 삭제' })).toBeDisabled()
    // 입력값은 유지된다
    expect(screen.getByLabelText('청약수량')).toHaveValue(20)

    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it('처음 조회가 실패하면(hydrated 이전) 다시 불러오기로 재조회한다', async () => {
    const { onSaved } = renderSection({
      mode: 'edit',
      record: RECORD,
      detail: DETAIL,
      status: 'error',
    })

    expect(await screen.findByText(/불러오지 못했습니다/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it('편집 원본(record)이 없는 edit 모드에서는 POST fallback 없이 저장이 잠긴다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(subscriptionResponse())
    renderSection({ mode: 'edit', record: null, detail: DETAIL })

    expect(screen.getByRole('button', { name: '청약 저장' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))
    expect(postCalls()).toHaveLength(0)
    expect(patchCalls()).toHaveLength(0)
  })
})
