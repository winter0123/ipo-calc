import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SaleSection from './SaleSection.tsx'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import type { Sale } from '@/entities/sale/index.ts'
import { fill } from '@/test/fill.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

// 캡처 읽기: 브라우저 OCR과 파서 결과만 흉내 낸다(파서 자체는 entities/sale 테스트가 검증).
vi.mock('@/shared/lib/ocr/recognizeImages.ts', () => ({
  recognizeImages: vi.fn(async () => [{ width: 1125, height: 2436, words: [] }]),
}))
vi.mock('@/entities/sale/index.ts', async () => {
  const actual = await vi.importActual<object>('@/entities/sale/index.ts')
  return {
    ...actual,
    parseCapture: () => ({
      kind: 'A_detail',
      broker: 'KIS',
      records: [
        {
          stockName: '테스트IPO',
          stockCode: '123456',
          saleDate: '2026-11-25',
          quantity: 30,
          salePrice: 30000,
          fee: 5,
          tax: 20,
          realizedProfit: 14975,
        },
      ],
    }),
  }
})

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
  subscriptionStartDate: '2026-11-20T09:00:00Z',
  subscriptionEndDate: '2026-11-21T17:00:00Z',
  listingDate: '2026-12-01T09:00:00Z',
  refundDate: null,
  refundDateEstimated: null,
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

// 다른 계좌의 동일 종목 청약 — 캡처 계좌불일치 회귀용
const OTHER_ACCOUNT_SUB_ITEM = {
  id: 66,
  subscribedAt: '2026-11-20T09:00:00Z',
  ipoId: 7,
  ipoName: '테스트IPO',
  ipoFixedPrice: 25000,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: null,
  investorName: '홍길동',
  securitiesFirmName: '한국투자증권',
  securitiesAccountId: 99,
  securitiesAccountName: '다른계좌',
}

interface SaleSummaryOverrides {
  id?: number
  quantity?: number
  amount?: number | null
  fee?: number | null
  tax?: number | null
  otherCost?: number | null
  status?: string
  soldAt?: string
}

const saleSummary = (overrides: SaleSummaryOverrides = {}) => ({
  id: overrides.id ?? 88,
  quantity: overrides.quantity ?? 3,
  amount: overrides.amount === undefined ? 36000 : overrides.amount,
  fee: overrides.fee === undefined ? 2000 : overrides.fee,
  tax: overrides.tax === undefined ? 100 : overrides.tax,
  otherCost: overrides.otherCost === undefined ? null : overrides.otherCost,
  status: overrides.status ?? 'ACTIVE',
  soldAt: overrides.soldAt ?? '2026-11-21T00:00:00Z',
})

const makeDetail = (sales: ReturnType<typeof saleSummary>[], remainingQuantity: number | null) => ({
  id: 55,
  subscribedAt: '2026-11-20T09:00:00Z',
  ipoId: 7,
  ipoName: '테스트IPO',
  ipoFixedPrice: 25000,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: {
    id: 77,
    allocatedQuantity: 10,
    allocatedAmount: 250000,
    allocationStatus: 'ACTIVE',
  },
  sales,
  remainingQuantity,
  investorName: '홍길동',
  securitiesFirmName: '미래에셋증권',
  securitiesAccountId: 10,
  securitiesAccountName: '위탁계좌',
})

const saleSource = (id: number, price: number | null, soldAt: string, overrides: object = {}) => ({
  id,
  allocationId: 77,
  quantity: 3,
  price,
  amount: price != null ? price * 3 : null,
  saleDate: soldAt,
  fee: 2000,
  tax: 100,
  otherCost: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: soldAt,
  updatedAt: soldAt,
  ...overrides,
})

const SPLIT_DETAIL = makeDetail(
  [
    saleSummary({
      id: 88,
      quantity: 3,
      amount: 36000,
      fee: 2000,
      tax: 100,
      soldAt: '2026-11-21T00:00:00Z',
    }),
    saleSummary({
      id: 89,
      quantity: 4,
      amount: 54000,
      fee: 2000,
      tax: 120,
      soldAt: '2026-11-22T00:00:00Z',
    }),
    saleSummary({
      id: 90,
      quantity: 3,
      amount: 33000,
      fee: 2000,
      tax: 130,
      soldAt: '2026-11-23T00:00:00Z',
    }),
  ],
  0,
)
const SPLIT_SOURCES = [
  saleSource(88, 12000, '2026-11-21T00:00:00Z'),
  saleSource(89, 13500, '2026-11-22T00:00:00Z', { quantity: 4, amount: 54000 }),
  saleSource(90, 11000, '2026-11-23T00:00:00Z'),
]

const MID_DETAIL = makeDetail(
  [
    saleSummary({ id: 88, quantity: 3, amount: 36000, soldAt: '2026-11-21T00:00:00Z' }),
    saleSummary({
      id: 89,
      quantity: 4,
      amount: 54000,
      fee: 2000,
      tax: 120,
      soldAt: '2026-11-22T00:00:00Z',
    }),
  ],
  3,
)
const MID_SOURCES = [
  saleSource(88, 12000, '2026-11-21T00:00:00Z'),
  saleSource(89, 13500, '2026-11-22T00:00:00Z', { quantity: 4, amount: 54000 }),
]

interface RenderOptions {
  detail?: InvestmentHistoryItem | null
  records?: Sale[]
  status?: 'loading' | 'error' | 'ready'
  saveDisabled?: boolean
  onSaved?: () => Promise<void>
}

function renderSection({
  detail = MID_DETAIL,
  records = MID_SOURCES,
  status = 'ready',
  saveDisabled = false,
  onSaved,
}: RenderOptions = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const onSavedMock = onSaved ?? vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
  const onDirtyChange = vi.fn()
  const onPendingChange = vi.fn()
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <SaleSection
        ipo={IPO}
        investor={INVESTOR}
        account={ACCOUNT}
        onSaved={onSavedMock}
        status={status}
        detail={detail}
        records={records}
        onDirtyChange={onDirtyChange}
        onPendingChange={onPendingChange}
        saveDisabled={saveDisabled}
      />
    </QueryClientProvider>,
  )
  return { ...utils, onSaved: onSavedMock, onDirtyChange, onPendingChange }
}

const postSalesCalls = () =>
  vi
    .mocked(apiFetch)
    .mock.calls.filter(([path, options]) => path === '/sales' && options?.method === 'POST')

const patchSaleCalls = (id: number) =>
  vi
    .mocked(apiFetch)
    .mock.calls.filter(([path, options]) => path === `/sales/${id}` && options?.method === 'PATCH')

function stubApi(calls: (path: string, options?: RequestInit) => unknown) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    const p = String(path)
    if (p.startsWith('/investment-history?')) {
      return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
    }
    return calls(p, options)
  })
}

describe('매도 섹션', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('10주 배정을 3건(3/4/3주)으로 나누어 팔면 목록에 모두 보이고 잔여 0이 된다', () => {
    renderSection({ detail: SPLIT_DETAIL, records: SPLIT_SOURCES })

    expect(screen.getByText('12,000원')).toBeInTheDocument()
    expect(screen.getByText('13,500원')).toBeInTheDocument()
    expect(screen.getByText('11,000원')).toBeInTheDocument()
    expect(screen.getByText('36,000원')).toBeInTheDocument()
    expect(screen.getByText('54,000원')).toBeInTheDocument()
    expect(screen.getByText('33,000원')).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('잔여')
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('0주')
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('10주')
  })

  it('잔여 0이면 +매도 추가가 잠기고 안내가 보이며, 기존 건의 수정·삭제는 유지된다', () => {
    renderSection({ detail: SPLIT_DETAIL, records: SPLIT_SOURCES })

    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled()
    expect(screen.getByText(/배정 잔여가 0주라 매도를 추가할 수 없습니다/)).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /매도 삭제/ })[0]).toBeEnabled()
    expect(screen.getAllByRole('button', { name: /매도 수정/ })[0]).toBeEnabled()
  })

  it('0주 배정이면 매도를 추가할 수 없고 기존 건 수정·삭제는 유지된다', () => {
    const zeroAlloc = {
      ...MID_DETAIL,
      allocation: { ...MID_DETAIL.allocation, allocatedQuantity: 0, allocatedAmount: 0 },
      remainingQuantity: 0,
    }
    renderSection({ detail: zeroAlloc, records: MID_SOURCES })

    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 수정/ })[0]).toBeEnabled()
  })

  it('기존 배정이 있으면 subscriptionId로 연결하고 배정수량을 다시 묻지 않는다', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return saleSource(91, 30000, '2026-12-02T00:00:00Z', { quantity: 1, amount: 30000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    expect(screen.queryByLabelText('배정수량 *')).not.toBeInTheDocument()
    // 남은 수량이 기본으로 들어간다
    expect(screen.getByLabelText('매도수량')).toHaveValue(3)
    await fill(screen.getByLabelText('매도수량'), '1')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    await waitFor(() => expect(postSalesCalls()).toHaveLength(1))
    const body = JSON.parse((postSalesCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({ quantity: 1, price: 30000, amount: 30000, subscriptionId: 55 })
    expect(body).not.toHaveProperty('allocatedQuantity')
    expect(body).not.toHaveProperty('ipoId')
    // 기존 매도가 있으므로 공모주수수료 기본값은 0원
    expect(body.fee).toBe(0)
    expect(body.tax).toBe(1000)
  })

  it('청약·배정이 아예 없으면 배정수량을 받고 문맥 id로 보내 서버 자동 생성에 맡긴다', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return saleSource(91, 11000, '2026-12-02T00:00:00Z', { quantity: 2, amount: 22000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection({ detail: null, records: [] })

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    expect(screen.getByLabelText('배정수량 *')).toBeInTheDocument()
    expect(screen.getByText(/청약·배정이 함께 만들어집니다/)).toBeInTheDocument()
    await fill(screen.getByLabelText('배정수량 *'), '5')
    await fill(screen.getByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '11000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    await waitFor(() => expect(postSalesCalls()).toHaveLength(1))
    const body = JSON.parse((postSalesCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({
      quantity: 2,
      price: 11000,
      amount: 22000,
      allocatedQuantity: 5,
      ipoId: 7,
      investorId: 1,
      securitiesAccountId: 10,
    })
    expect(body).not.toHaveProperty('subscriptionId')
    // 매도 기록이 없었으므로 공모주수수료 기본값은 2,000원
    expect(body.fee).toBe(2000)
  })

  it('청약만 있고 배정이 없으면 subscriptionId와 배정수량을 함께 보낸다', async () => {
    const subscriptionOnly = { ...MID_DETAIL, allocation: null, sales: [], remainingQuantity: null }
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return saleSource(91, 11000, '2026-12-02T00:00:00Z', {
          quantity: 2,
          amount: 22000,
          allocationId: 78,
        })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection({ detail: subscriptionOnly, records: [] })

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('배정수량 *'), '5')
    await fill(screen.getByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '11000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    await waitFor(() => expect(postSalesCalls()).toHaveLength(1))
    const body = JSON.parse((postSalesCalls()[0][1] as RequestInit).body as string)
    expect(body).toMatchObject({ subscriptionId: 55, allocatedQuantity: 5 })
    expect(body).not.toHaveProperty('ipoId')
  })

  it('이중 클릭해도 POST는 정확히 한 번만 보낸다', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return saleSource(91, 30000, '2026-12-02T00:00:00Z', { quantity: 1, amount: 30000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '1')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    const saveButton = screen.getByRole('button', { name: '매도 저장' })
    await userEvent.click(saveButton)
    await userEvent.click(saveButton)

    await waitFor(() => expect(postSalesCalls()).toHaveLength(1))
  })

  it('서버가 거절하면(ApiError, 예: 잔여 초과) 서버 메시지를 그대로 보여 주고 입력을 유지한다 — 목록 재조회·자동 재전송 없음', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        throw new ApiError(
          400,
          '매도 수량이 배정 잔여 수량을 초과합니다.',
          '매도 수량이 배정 잔여 수량을 초과합니다.',
        )
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const { onSaved } = renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('배정 잔여 수량을 초과')
    expect(screen.getByLabelText('매도수량')).toHaveValue(2)
    // 서버가 거절했으므로(반영되지 않았으므로) POST 재전송도 목록 재조정도 없다
    expect(postSalesCalls()).toHaveLength(1)
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('응답을 받지 못하면(네트워크·timeout) 목록·잔여를 재조회하고 수동 판단을 안내한다 — 자동 재전송 없음', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        throw new TypeError('Failed to fetch')
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const { onSaved } = renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 요청이 도착했는지')
    expect(screen.getByLabelText('매도수량')).toHaveValue(2)
    expect(postSalesCalls()).toHaveLength(1)
    // 서버 반영 여부가 불명확하므로 목록·잔여를 다시 읽어 사용자가 판단하게 한다(POST 재전송 아님)
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(postSalesCalls()).toHaveLength(1)
  })

  it('서버가 5xx를 돌려주면 응답 유실과 동일하게 처리한다 — 자동 재전송 없이 재조회 후 수동 판단', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        throw new ApiError(500, '서버 내부 오류')
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const { onSaved } = renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    // 5xx는 서버가 처리했는지 알 수 없으므로 응답 유실과 같은 안내를 한다
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 요청이 도착했는지')
    expect(screen.getByLabelText('매도수량')).toHaveValue(2)
    expect(postSalesCalls()).toHaveLength(1)
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(postSalesCalls()).toHaveLength(1)
  })

  it.each([
    [400, '입력값을 확인해 주세요.'],
    [403, '서버가 거절했습니다(403)'],
    // 404는 배정을 찾을 수 없다는 고정 안내로 표시된다
    [404, '배정을 찾을 수 없습니다.'],
    [409, '서버가 거절했습니다(409)'],
  ] as const)(
    '서버가 %i를 돌려주면 ' + '"%s"을(를) 표시하고 목록 재조회·재전송은 하지 않는다',
    async (status, expectedMessage) => {
      stubApi((p, options) => {
        if (p === '/sales' && options?.method === 'POST') {
          throw new ApiError(status, `서버가 거절했습니다(${status})`)
        }
        throw new Error(`unexpected path: ${p}`)
      })
      const { onSaved } = renderSection()

      await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
      await fill(screen.getByLabelText('매도수량'), '2')
      await fill(screen.getByLabelText('매도단가'), '30000')
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
      await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(expectedMessage)
      expect(screen.getByLabelText('매도수량')).toHaveValue(2)
      expect(postSalesCalls()).toHaveLength(1)
      expect(onSaved).not.toHaveBeenCalled()
    },
  )

  it('저장 후 상세 재조회가 끝나기 전에는 다음 추가를 잠근다', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return saleSource(91, 30000, '2026-12-02T00:00:00Z', { quantity: 1, amount: 30000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    let resolveSaved: (() => void) | null = null
    const onSaved = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveSaved = resolve
        }),
    )
    renderSection({ onSaved })

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '1')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    // POST 성공 후에도 재조회가 끝나기 전에는 진행 중 라벨로 잠긴다
    expect(await screen.findByRole('button', { name: '저장 중...' })).toBeDisabled()
    expect(postSalesCalls()).toHaveLength(1)

    resolveSaved!()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: '저장 중...' })).not.toBeInTheDocument(),
    )
    expect(postSalesCalls()).toHaveLength(1)
  })

  it('공모주수수료 기본값: ACTIVE 매도가 없으면 2,000원, 있으면 0원', async () => {
    stubApi(() => {
      throw new Error('unexpected call')
    })
    const { unmount } = renderSection({ detail: null, records: [] })
    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    expect(screen.getByLabelText('공모주수수료')).toHaveValue('2,000')
    unmount()

    renderSection({ detail: MID_DETAIL, records: MID_SOURCES })
    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    expect(screen.getByLabelText('공모주수수료')).toHaveValue('0')
  })
})

describe('매도 수정·삭제', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('수량만 바꾸면 PATCH에 변경 필드와 재계산한 amount만 담는다 (단가는 원본 유지)', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'PATCH') {
        return saleSource(88, 12000, '2026-11-21T00:00:00Z', { quantity: 5, amount: 60000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
      '5',
    )
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(patchSaleCalls(88)).toHaveLength(1))
    const body = JSON.parse((patchSaleCalls(88)[0][1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 5, amount: 60000 })
  })

  it('날짜만 바꾸면 saleDate만 보내고 단가·금액·수수료는 건드리지 않는다', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'PATCH') {
        return saleSource(88, 12000, '2026-11-25T00:00:00Z')
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    const dateInput = screen.getByLabelText('매도일', {
      selector: '#manage-edit-sale-date-88',
    })
    fireEvent.change(dateInput, { target: { value: '2026-11-25' } })
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(patchSaleCalls(88)).toHaveLength(1))
    const body = JSON.parse((patchSaleCalls(88)[0][1] as RequestInit).body as string)
    expect(body).toEqual({ saleDate: '2026-11-25T09:00' })
  })

  it('원본에 단가가 없으면 수량만 바꿀 수 없고, 단가를 입력하면 amount까지 계산한다', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'PATCH') {
        return saleSource(88, 12500, '2026-11-21T00:00:00Z', { quantity: 5, amount: 62500 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const nullPriceRecord = saleSource(88, null, '2026-11-21T00:00:00Z', { amount: 36000 })
    const nullPriceDetail = {
      ...MID_DETAIL,
      sales: [saleSummary({ id: 88, quantity: 3, amount: 36000, fee: 2000, tax: 100 })],
      remainingQuantity: 7,
    }
    renderSection({ detail: nullPriceDetail, records: [nullPriceRecord] })

    // 원본 금액은 보존하고 단가는 미입력으로 표시한다
    expect(screen.getByText('미입력')).toBeInTheDocument()
    expect(screen.getByText('36,000원')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
      '5',
    )
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이 매도는 단가가 기록에 없습니다')
    expect(patchSaleCalls(88)).toHaveLength(0)

    // 단가를 입력하면 그 값으로 amount까지 계산해 보낸다
    await fill(
      screen.getByLabelText('매도단가', { selector: '#manage-edit-sale-price-88' }),
      '12500',
    )
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(patchSaleCalls(88)).toHaveLength(1))
    const body = JSON.parse((patchSaleCalls(88)[0][1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 5, price: 12500, amount: 62500 })
  })

  it('원본에 단가가 없으면 amount가 null이어도 금액을 추정하지 않는다', () => {
    const nullRecord = saleSource(88, null, '2026-11-21T00:00:00Z', { amount: null })
    const nullAmountDetail = {
      ...MID_DETAIL,
      sales: [saleSummary({ id: 88, quantity: 3, amount: null })],
      remainingQuantity: 7,
    }
    renderSection({ detail: nullAmountDetail, records: [nullRecord] })

    expect(screen.getByText('미입력')).toBeInTheDocument()
    expect(screen.getAllByText('-').length).toBeGreaterThan(0)
  })

  it('변경이 없으면 PATCH를 보내지 않고 안내만 보여 준다', async () => {
    stubApi(() => {
      throw new Error('unexpected call')
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    expect(await screen.findByText('변경된 내용이 없습니다.')).toBeInTheDocument()
    expect(patchSaleCalls(88)).toHaveLength(0)
  })

  it('공모주수수료만 바꾸면 fee만 보내고, tax를 비워 두면 nullable tax를 덮지 않는다', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'PATCH') {
        return saleSource(88, 12000, '2026-11-21T00:00:00Z', { fee: 3000 })
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const feeNullRecord = saleSource(88, 12000, '2026-11-21T00:00:00Z', { fee: 2000, tax: null })
    const taxNullDetail = {
      ...MID_DETAIL,
      sales: [saleSummary({ id: 88, quantity: 3, amount: 36000, fee: 2000, tax: null })],
      remainingQuantity: 7,
    }
    renderSection({ detail: taxNullDetail, records: [feeNullRecord] })

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('공모주수수료', { selector: '#manage-edit-sale-ipoFee-88' }),
      '3000',
    )
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(patchSaleCalls(88)).toHaveLength(1))
    const body = JSON.parse((patchSaleCalls(88)[0][1] as RequestInit).body as string)
    expect(body).toEqual({ fee: 3000 })
  })

  it('수정 저장이 끝나기 전에는 다른 조작을 잠그고 자동 재전송하지 않는다', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'PATCH') {
        throw new ApiError(500, '서버 오류')
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
      '5',
    )
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('서버 오류')
    expect(patchSaleCalls(88)).toHaveLength(1)
    // 수정 폼이 열린 채로 입력이 보존된다
    expect(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
    ).toHaveValue(5)
  })

  it('삭제 확인 시 DELETE를 호출하고 onSaved로 갱신한다', async () => {
    stubApi((p, options) => {
      if (p === '/sales/88' && options?.method === 'DELETE') {
        return undefined
      }
      throw new Error(`unexpected path: ${p}`)
    })
    const { onSaved } = renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 삭제' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('매도 3주')
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.some(
          ([p, o]) => p === '/sales/88' && (o as RequestInit | undefined)?.method === 'DELETE',
        ),
    ).toBe(true)
  })

  it('취소된(CANCELLED) 매도는 수정·삭제 버튼이 비활성화된다', () => {
    const cancelled = saleSummary({ id: 88, status: 'CANCELLED' })
    const detailWithCancelled = { ...MID_DETAIL, sales: [cancelled] }
    const cancelledRecord = saleSource(88, 12000, '2026-11-21T00:00:00Z', { status: 'CANCELLED' })
    renderSection({ detail: detailWithCancelled, records: [cancelledRecord] })

    expect(screen.getByRole('button', { name: '2026-11-21 매도 수정' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '2026-11-21 매도 삭제' })).toBeDisabled()
  })

  it('조회 갱신 실패(hydrated 후 error)면 배너를 보여 주고 잠근 뒤, 다시 불러오기로 복구한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const renderTree = (status: 'ready' | 'error') => (
      <QueryClientProvider client={queryClient}>
        <SaleSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          status={status}
          detail={MID_DETAIL}
          records={MID_SOURCES}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>
    )
    const { rerender } = render(renderTree('ready'))

    rerender(renderTree('error'))

    expect(
      await screen.findByText(/갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 삭제/ })[0]).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 수정/ })[0]).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  })

  it('아직 갱신 중(status=loading, hydrated 이전)이면 폼을 열지 않고 안내만 보여 준다', () => {
    renderSection({ status: 'loading' })

    expect(screen.getByRole('status')).toHaveTextContent('매도 기록을 불러오는 중...')
    expect(screen.queryByRole('button', { name: '+ 매도 추가' })).not.toBeInTheDocument()
  })

  it('다른 섹션 저장 중(saveDisabled)이면 저장·삭제·추가를 잠근다', () => {
    renderSection({ saveDisabled: true })

    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 삭제/ })[0]).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 수정/ })[0]).toBeDisabled()
  })
})

describe('매도 캡처', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('다른 계좌의 청약에 대응하는 캡처도 계좌를 바꾸지 않고 현재 화면 기준으로 채운다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const p = String(path)
      if (p.startsWith('/investment-history?') && p.includes('investorId=1')) {
        // 다른 계좌(99)의 동일 종목 ACTIVE 청약만 매칭됨
        return {
          content: [OTHER_ACCOUNT_SUB_ITEM],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }
      }
      if (p.startsWith('/investment-history?')) {
        return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    const input = screen.getByLabelText(/캡처 \(여러 장 가능\)/)
    await userEvent.upload(input, new File(['x'], 'capture.png', { type: 'image/png' }))

    expect(await screen.findByText(/다른 계좌의 청약에 대응합니다/)).toBeInTheDocument()
    expect(screen.getByLabelText('매도수량')).toHaveValue(30)
    expect(screen.getByLabelText('매도단가')).toHaveValue('30,000')
    // 현재 화면의 공모주·계좌 기준은 그대로다(자동 전환 금지)
    expect(screen.getByText(/증권계좌는 위에서 고른 계좌로 고정됩니다/)).toBeInTheDocument()
  })
})

describe('매도 조회 실패 복구', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('처음 조회가 실패하면(hydrated 이전) 다시 불러오기로 재조회한다', async () => {
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    renderSection({ status: 'error', onSaved })

    expect(await screen.findByText(/매도 기록을 불러오지 못했습니다/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  })
})

describe('매도 화면 view 상호배타', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('추가 폼에 입력이 있으면 다른 매도 편집으로 전환하기 전에 확인을 묻는다', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '2')
    await userEvent.click(screen.getByRole('button', { name: '2026-11-22 매도 수정' }))

    expect(screen.getByRole('alertdialog')).toHaveTextContent('저장하지 않은 입력이 있어요')
    // 계속 작성 — 추가 폼과 입력값을 유지한 채 닫힌다
    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(screen.getByLabelText('매도수량')).toHaveValue(2)

    // 확정하면 추가 폼이 닫히고 수정 폼이 열린다
    await userEvent.click(screen.getByRole('button', { name: '2026-11-22 매도 수정' }))
    await userEvent.click(screen.getByRole('button', { name: '닫고 진행' }))
    expect(
      screen.queryByLabelText('매도수량', { selector: '#manage-sale-quantity' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('form', { name: '매도 수정 폼 89' })).toBeInTheDocument()
  })

  it('수정 중인 입력이 있으면 다른 매도 편집으로 전환하기 전에 확인을 묻는다', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
      '7',
    )
    await userEvent.click(screen.getByRole('button', { name: '2026-11-22 매도 수정' }))

    expect(screen.getByRole('alertdialog')).toHaveTextContent('저장하지 않은 입력이 있어요')
    // 계속 작성 — 아직 88을 수정 중이고 입력값이 유지된다
    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
    ).toHaveValue(7)
    expect(patchSaleCalls(89)).toHaveLength(0)

    // 확정하면 다른 매도로 넘어간다
    await userEvent.click(screen.getByRole('button', { name: '2026-11-22 매도 수정' }))
    await userEvent.click(screen.getByRole('button', { name: '닫고 진행' }))
    expect(screen.getByRole('form', { name: '매도 수정 폼 89' })).toBeInTheDocument()
    expect(
      screen.queryByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
    ).not.toBeInTheDocument()
  })

  it('수정 모드에서는 +매도 추가 버튼이 보이지 않는다(동시에 열 수 없음)', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    expect(screen.queryByRole('button', { name: '+ 매도 추가' })).not.toBeInTheDocument()
    // 다른 매도 편집으로 전환해도 마찬가지다
    await userEvent.click(screen.getByRole('button', { name: '2026-11-22 매도 수정' }))
    expect(screen.queryByRole('button', { name: '+ 매도 추가' })).not.toBeInTheDocument()
  })

  it('입력이 없으면 확인 없이 바로 전환된다', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('form', { name: '매도 수정 폼 88' })).toBeInTheDocument()
  })

  it('저장 진행 중에는 다른 매도로 전환할 수 없다', async () => {
    stubApi((p, options) => {
      if (p === '/sales' && options?.method === 'POST') {
        return new Promise(() => {}) // 응답을 끝내 돌려주지 않는다
      }
      throw new Error(`unexpected path: ${p}`)
    })
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '1')
    await fill(screen.getByLabelText('매도단가'), '30000')
    await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    // 저장이 진행되는 동안 다른 매도 수정 버튼은 잠긴다
    const editButtons = screen.getAllByRole('button', { name: /매도 수정/ })
    for (const button of editButtons) expect(button).toBeDisabled()
    expect(screen.queryByRole('button', { name: '+ 매도 추가' })).not.toBeInTheDocument()
    expect(screen.getByRole('form', { name: '매도 추가 폼' })).toBeInTheDocument()
  })

  it('추가 폼의 취소를 누르면 입력 중 확인을 거쳐 닫힌다', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await fill(screen.getByLabelText('매도수량'), '2')
    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.getByRole('alertdialog')).toHaveTextContent('저장하지 않은 입력이 있어요')
    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(screen.getByLabelText('매도수량')).toHaveValue(2)

    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    await userEvent.click(screen.getByRole('button', { name: '닫고 진행' }))
    expect(screen.queryByRole('form', { name: '매도 추가 폼' })).not.toBeInTheDocument()
  })

  it('수정 폼의 취소를 누르면 입력 중 확인을 거쳐 닫히고 PATCH는 보내지 않는다', async () => {
    renderSection()

    await userEvent.click(screen.getByRole('button', { name: '2026-11-21 매도 수정' }))
    await fill(
      screen.getByLabelText('매도수량', { selector: '#manage-edit-sale-quantity-88' }),
      '9',
    )
    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.getByRole('alertdialog')).toHaveTextContent('저장하지 않은 입력이 있어요')
    await userEvent.click(screen.getByRole('button', { name: '닫고 진행' }))

    expect(screen.queryByRole('form', { name: '매도 수정 폼 88' })).not.toBeInTheDocument()
    expect(patchSaleCalls(88)).toHaveLength(0)
  })
})

describe('매도 진행 단계(derive)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('배정만 있고 매도가 없으면 매도 전이다', () => {
    const noSales = {
      ...MID_DETAIL,
      sales: [],
      remainingQuantity: 10,
    }
    renderSection({ detail: noSales, records: [] })

    expect(screen.getByTestId('sale-phase')).toHaveTextContent('매도 전')
  })

  it('0주 배정이면 미배정(0주)이다', () => {
    const zeroAlloc = {
      ...MID_DETAIL,
      allocation: { ...MID_DETAIL.allocation, allocatedQuantity: 0, allocatedAmount: 0 },
      sales: [],
      remainingQuantity: 0,
    }
    renderSection({ detail: zeroAlloc, records: [] })

    expect(screen.getByTestId('sale-phase')).toHaveTextContent('미배정(0주)')
  })

  it('청약·배정이 아직 없으면 배정 미기록이다', () => {
    renderSection({ detail: null, records: [] })

    expect(screen.getByTestId('sale-phase')).toHaveTextContent('배정 미기록')
  })

  it('10주 배정에 매도 7주면 부분 매도, 잔여 0이면 매도 완료, 하나를 지우면 다시 부분 매도다', () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const tree = (detail: typeof SPLIT_DETAIL) => (
      <QueryClientProvider client={queryClient}>
        <SaleSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          status="ready"
          detail={detail}
          records={SPLIT_SOURCES}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>
    )
    const { rerender } = render(tree(SPLIT_DETAIL))
    // 3+4+3 = 10주 — 매도 완료
    expect(screen.getByTestId('sale-phase')).toHaveTextContent('매도 완료')

    // 한 건을 지운 상태로 재조회되면(88, 89만 남음 + 잔여 3) 부분 매도
    rerender(
      tree({
        ...SPLIT_DETAIL,
        sales: [
          saleSummary({ id: 88, quantity: 3, amount: 36000, soldAt: '2026-11-21T00:00:00Z' }),
          saleSummary({
            id: 89,
            quantity: 4,
            amount: 54000,
            fee: 2000,
            tax: 120,
            soldAt: '2026-11-22T00:00:00Z',
          }),
        ],
        remainingQuantity: 3,
      }),
    )
    expect(screen.getByTestId('sale-phase')).toHaveTextContent('부분 매도')
  })

  it('매도 전 → 부분 매도 → 매도 완료 전이는 잔여 수량 기준으로 정확히 동작한다', () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const noSales = { ...MID_DETAIL, sales: [], remainingQuantity: 10 }
    const tree = (detail: typeof MID_DETAIL) => (
      <QueryClientProvider client={queryClient}>
        <SaleSection
          ipo={IPO}
          investor={INVESTOR}
          account={ACCOUNT}
          onSaved={onSaved}
          status="ready"
          detail={detail}
          records={MID_SOURCES}
          onDirtyChange={vi.fn()}
        />
      </QueryClientProvider>
    )
    const { rerender } = render(tree(noSales))
    expect(screen.getByTestId('sale-phase')).toHaveTextContent('매도 전')

    rerender(tree(MID_DETAIL)) // 3+4=7주 매도, 잔여 3
    expect(screen.getByTestId('sale-phase')).toHaveTextContent('부분 매도')

    rerender(tree(SPLIT_DETAIL)) // 10주 전부 매도, 잔여 0
    expect(screen.getByTestId('sale-phase')).toHaveTextContent('매도 완료')
  })
})
