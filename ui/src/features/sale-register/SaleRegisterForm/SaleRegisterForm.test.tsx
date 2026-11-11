import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SaleRegisterForm from './SaleRegisterForm.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

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
          stockName: '카카오뱅크',
          stockCode: '323410',
          saleDate: '2026-10-25',
          quantity: 30,
          salePrice: 12000,
          fee: 5,
          tax: 20,
          realizedProfit: 59975,
        },
      ],
    }),
    allocationUnitPrice: (
      fixedPrice: number | null | undefined,
      allocatedAmount?: number,
      allocatedQuantity?: number,
    ) => {
      if (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0) {
        return Math.round(allocatedAmount / allocatedQuantity)
      }
      return fixedPrice ?? null
    },
  }
})

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/apiError.ts'

const mutationFnMock = vi.fn(() => Promise.resolve({}))

vi.mock('../useSaleRegister.ts', () => ({
  useSaleRegister: () => {
    const queryClient = useQueryClient()
    return useMutation({
      mutationFn: mutationFnMock,
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
        mockNavigate('/investments')
      },
    })
  },
}))

const INVESTORS = [
  {
    id: 1,
    displayName: '홍길동',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
]

const IPO = {
  id: 10,
  companyName: '카카오뱅크',
  ipoName: '카카오뱅크',
  stockCode: '323410',
  roundLabel: null,
  lowerBandPrice: null,
  upperBandPrice: null,
  fixedPrice: 10000,
  subscriptionStartDate: '2026-10-01',
  subscriptionEndDate: '2026-10-02',
  listingDate: '2026-10-23',
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

const SECURITIES_ACCOUNTS = [
  {
    id: 1,
    investorId: 1,
    securitiesFirmId: 1,
    securitiesFirmName: '테스트증권',
    accountNumber: null,
    accountName: '개인 MTS 계좌',
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
]

// useIpoInsight 응답은 스키마 전체를 포함해야 Zod 파싱을 통과한다.
const insightWith = (
  underwriters: Array<{ name: string; role: string | null; shares: number }>,
) => ({
  syncedAt: null,
  sourceReportName: null,
  sourceUrl: null,
  ceoName: null,
  establishedOn: null,
  address: null,
  parValue: null,
  capitalStock: null,
  largestShareholderName: null,
  largestShareholderRatio: null,
  institutionalCompetitionRate: null,
  lockUpCommitmentRatio: null,
  financials: [],
  floatSchedule: [],
  underwriters,
})

const SUBSCRIPTION_HISTORY_ITEM = {
  id: 1,
  subscribedAt: '2026-09-01T09:00:00Z',
  ipoId: 10,
  ipoName: '카카오뱅크',
  ipoFixedPrice: 10000,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: null,
  investorName: null,
  securitiesFirmName: null,
  securitiesAccountId: 1,
  securitiesAccountName: '개인 MTS 계좌',
}

const SUBSCRIPTION_HISTORY = {
  content: [SUBSCRIPTION_HISTORY_ITEM],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
}

const EMPTY_HISTORY_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 1,
}

const CONNECTED_HISTORY_PAGE = {
  content: [{ ...SUBSCRIPTION_HISTORY_ITEM, allocation: null, remainingQuantity: 30 }],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 1,
}

const ALLOCATION_DETAIL = {
  id: 1,
  subscribedAt: '2026-09-01T09:00:00Z',
  ipoId: 10,
  ipoName: '카카오뱅크',
  ipoFixedPrice: 10000,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: {
    id: 1,
    allocatedQuantity: 50,
    allocatedAmount: null,
    allocationStatus: 'ACTIVE',
  },
  sales: [],
  remainingQuantity: 30,
  investorName: null,
  securitiesFirmName: null,
  securitiesAccountId: 1,
  securitiesAccountName: '개인 MTS 계좌',
}

function defaultMock(
  overrides: {
    ipo?: unknown
    detail?: unknown
    accounts?: unknown
    insight?: unknown
    history?: unknown
    lookup?: unknown
    ipoPending?: boolean
    detailPending?: boolean
    lookupPending?: boolean
    lookupError?: unknown
    accountsError?: unknown
    insightError?: unknown
  } = {},
) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    const p = String(path)
    const isGet = !options || options.method === 'GET' || options.method === undefined
    if (p === '/investors' && isGet) return INVESTORS
    if (p === '/ipos' && isGet) return [IPO]
    if (p === '/ipos/10' && isGet) {
      if (overrides.ipoPending) return new Promise(() => {})
      return overrides.ipo ?? IPO
    }
    if (p === '/ipos/10/insight' && isGet) {
      if (overrides.insightError) throw overrides.insightError
      return overrides.insight ?? insightWith([])
    }
    if (p.startsWith('/securities-accounts')) {
      if (overrides.accountsError) throw overrides.accountsError
      return overrides.accounts ?? SECURITIES_ACCOUNTS
    }
    if (p === '/investment-history/1' && isGet) {
      if (overrides.detailPending) return new Promise(() => {})
      return overrides.detail ?? ALLOCATION_DETAIL
    }
    if (/^\/investment-history\/\d+/.test(p) && isGet) throw new Error('not found')
    if (p.startsWith('/investment-history') && isGet) {
      if (p.includes('securitiesAccountId=')) {
        if (overrides.lookupError) throw overrides.lookupError
        if (overrides.lookupPending) return new Promise(() => {})
        return overrides.lookup ?? EMPTY_HISTORY_PAGE
      }
      return overrides.history ?? SUBSCRIPTION_HISTORY
    }
    throw new Error(`unexpected path: ${String(path)}`)
  })
}

function renderForm(url = '/sales/register') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <SaleRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** 청약 프리셋 링크로 들어와 배정 기준 기본값(공모가·상장일·수량)이 채워질 때까지 기다린다. */
async function setupForm() {
  renderForm('/sales/register?subscriptionId=1')
  await waitFor(() => {
    expect(screen.getByText('홍길동')).toBeInTheDocument()
  })
  await waitFor(() => {
    expect(screen.getByLabelText('공모주')).toHaveValue('카카오뱅크')
  })
  await waitFor(() => {
    expect(screen.getByText('남은 수량')).toBeInTheDocument()
  })
  await waitFor(() => {
    expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('10,000')
  })
}

// 금액 칸은 천 단위 쉼표가 붙어 보이므로 숫자만 비교한다(표시 형식은 별도 테스트).
const value = (label: string) =>
  (screen.getByLabelText(label) as HTMLInputElement).value.replaceAll(',', '')

describe('SaleRegisterForm', () => {
  beforeEach(() => {
    localStorage.setItem('currentInvestorId', '1')
    mockNavigate.mockClear()
    mutationFnMock.mockReset()
    mutationFnMock.mockResolvedValue({})
    defaultMock()
  })

  it('투자자 목록을 로드하고 현재 투자자 이름을 표시함', async () => {
    renderForm('/sales/register?subscriptionId=1')
    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument()
    })
  })

  it('문맥 없는 /sales/register는 공모주 청약 목록으로 가는 안내만 보여 주고 폼을 렌더링하지 않는다', async () => {
    renderForm('/sales/register')
    const link = await screen.findByRole('link', { name: '공모주 청약 목록으로 이동' })
    expect(link).toHaveAttribute('href', '/subscriptions')
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
  })

  it('공모주별 링크(ipoId)로 들어오면 공모주는 고정 표시되고 청약 선택 칸은 없다', async () => {
    defaultMock({ insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]) })
    renderForm('/sales/register?ipoId=10')
    const ipoInput = await screen.findByLabelText('공모주')
    await waitFor(() => expect(ipoInput).toHaveValue('카카오뱅크'))
    expect(screen.queryByLabelText('청약')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '청약' })).not.toBeInTheDocument()
  })

  it('잘못된 ipoId(abc)는 오류 안내만 보여 주고 폼을 렌더링하지 않는다', async () => {
    renderForm('/sales/register?ipoId=abc')
    expect(await screen.findByRole('alert')).toHaveTextContent('공모주를 찾을 수 없습니다.')
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
  })

  it('잘못된 subscriptionId(abc)는 오류 안내만 보여 주고 폼을 렌더링하지 않는다', async () => {
    renderForm('/sales/register?subscriptionId=abc')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다.',
    )
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
  })

  it('공모주별 링크에서 주관사 계좌가 1개면 기본 선택되고, 연결이 없으면 배정수량을 요구한다', async () => {
    defaultMock({ insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]) })
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByLabelText('증권계좌 *')).toHaveValue('1')
    })
    // 연결이 없다는 조회가 끝나면 배정수량 칸이 나타난다
    await waitFor(() => {
      expect(screen.getByLabelText(/^배정수량/)).toBeInTheDocument()
    })
    // 번호가 없는 계좌도 정상적으로 선택된다
    expect(screen.getByRole('option', { name: /개인 MTS 계좌/ })).toBeInTheDocument()
  })

  it('공모주별 링크에서 기존 연결(청약+배정)이 있으면 배정수량 없이 그 배정에 연결한다', async () => {
    defaultMock({
      insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]),
      lookup: CONNECTED_HISTORY_PAGE,
    })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByText('남은 수량')).toBeInTheDocument()
    })
    expect(screen.queryByLabelText(/^배정수량/)).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('10,000')
    })
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() => expect(spy).toHaveBeenCalled())
    const payload = spy.mock.calls[0][0]
    expect(payload).toMatchObject({ subscriptionId: 1, ipoId: 10, investorId: 1 })
    expect(payload.allocatedQuantity).toBeUndefined()
    expect(payload.allocationId).toBeUndefined()
  })

  it('공모주별 링크에서 청약만 있고 배정이 없으면 배정수량을 입력받아 등록 시 배정을 함께 만든다', async () => {
    defaultMock({
      insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]),
      lookup: { ...CONNECTED_HISTORY_PAGE, content: [SUBSCRIPTION_HISTORY_ITEM] },
      detail: { ...ALLOCATION_DETAIL, allocation: null, remainingQuantity: null },
    })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByLabelText(/^배정수량/)).toBeInTheDocument()
    })
    await fill(screen.getByLabelText(/^배정수량/), '12')
    await fill(screen.getByLabelText('매도수량'), '12')
    await fill(screen.getByLabelText('매도단가'), '15000')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() => expect(spy).toHaveBeenCalled())
    const payload = spy.mock.calls[0][0]
    expect(payload).toMatchObject({ subscriptionId: 1, allocatedQuantity: 12 })
  })

  it('링크의 공모주와 청약이 서로 다르면 등록하지 않고 안내만 보여 준다', async () => {
    defaultMock({ detail: { ...ALLOCATION_DETAIL, ipoId: 20 } })
    renderForm('/sales/register?ipoId=10&subscriptionId=1')
    expect(
      await screen.findByText(
        '이 링크의 공모주와 청약 정보가 서로 다릅니다. 공모주 청약 목록에서 다시 열어 주세요.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
  })

  it('남의 청약 링크면 등록하지 않고 안내만 보여 준다', async () => {
    renderForm('/sales/register?subscriptionId=999')
    expect(
      await screen.findByText('이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다.'),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
  })

  it('참고 표시가 렌더링됨 (AC-018)', async () => {
    await setupForm()
    expect(screen.getByText('남은 수량')).toBeInTheDocument()
  })

  describe('기본값 자동 입력', () => {
    it('매도일은 해당 공모주 상장일, 매도단가는 공모가, 매도수량은 남은 수량이 먼저 들어간다', async () => {
      await setupForm()
      expect(value('매도일')).toBe('2026-10-23')
      expect(value('매도단가')).toBe('10000')
      expect(value('매도수량')).toBe('30')
    })

    it('공모주수수료는 2000원, 세금 및 기타 수수료는 비어 있다', async () => {
      await setupForm()
      expect(value('공모주수수료')).toBe('2000')
      expect(value('세금 및 기타 수수료 (선택)')).toBe('')
    })

    it('기본값을 넣은 뒤에도 사람이 고칠 수 있다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도일'), '2026-10-24')
      await fill(screen.getByLabelText('매도수량'), '20')
      await fill(screen.getByLabelText('공모주수수료'), '1500')
      expect(value('매도일')).toBe('2026-10-24')
      expect(value('매도수량')).toBe('20')
      expect(value('공모주수수료')).toBe('1500')
    })

    it('이미 매도 기록이 있는 배정은 공모주수수료가 0원으로 시작한다(배정 1건당 한 번)', async () => {
      defaultMock({
        detail: {
          ...ALLOCATION_DETAIL,
          sales: [
            {
              id: 5,
              quantity: 20,
              amount: 200000,
              fee: 2000,
              tax: 0,
              otherCost: null,
              status: 'ACTIVE',
              soldAt: '2026-10-23T09:00:00',
            },
          ],
        },
      })
      await setupForm()
      expect(value('공모주수수료')).toBe('0')
      expect(screen.getByText(/이미 매도 기록이 있어 0원으로 시작합니다/)).toBeInTheDocument()
    })

    it('상장일이 없으면 매도일은 비어 있다', async () => {
      defaultMock({ ipo: { ...IPO, listingDate: null } })
      await setupForm()
      expect(value('매도일')).toBe('')
    })

    it('확정 공모가가 없으면 배정 평균 단가를 매도단가 기본값으로 사용한다', async () => {
      defaultMock({
        ipo: { ...IPO, fixedPrice: null },
        detail: {
          ...ALLOCATION_DETAIL,
          ipoFixedPrice: null,
          allocation: { ...ALLOCATION_DETAIL.allocation, allocatedAmount: 475000 },
        },
      })
      renderForm('/sales/register?subscriptionId=1')
      await waitFor(() => expect(screen.getByText('홍길동')).toBeInTheDocument())
      await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('9,500'))
    })
  })

  describe('입력 편의', () => {
    it('공모주는 고정 표시되고 청약 선택 칸은 없다', async () => {
      renderForm('/sales/register?subscriptionId=1')
      const ipoInput = await screen.findByLabelText('공모주')
      await waitFor(() => expect(ipoInput).toHaveValue('카카오뱅크'))
      expect(screen.queryByLabelText('청약')).not.toBeInTheDocument()
    })

    it('금액은 천 단위 쉼표로 보이고 저장 값은 숫자다', async () => {
      const spy = vi.fn().mockResolvedValue({ id: 1 })
      mutationFnMock.mockImplementation(spy)
      await setupForm()
      expect(screen.getByLabelText('매도단가')).toHaveAttribute('inputmode', 'numeric')
      await fill(screen.getByLabelText('매도단가'), '15000')
      expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('15,000')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => expect(spy).toHaveBeenCalled())
      expect(spy.mock.calls[0][0]).toMatchObject({
        price: 15000,
        amount: 450000,
        subscriptionId: 1,
      })
    })

    it('실현손익은 음수도 입력할 수 있다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '9000')
      expect(screen.getByLabelText('실현손익')).toHaveDisplayValue('-30,000')
    })

    it('계산식 설명은 접혀 있다가 펼치면 보인다', async () => {
      await setupForm()
      expect(screen.getByText('계산식 보기')).toBeInTheDocument()
      expect(screen.getByText(/공모주수수료는 빼지/).closest('details')).not.toHaveAttribute('open')
    })

    it('배정이 없으면 등록 시 배정수량과 공모주가 함께 전송된다', async () => {
      defaultMock({
        detail: {
          ...ALLOCATION_DETAIL,
          allocation: null,
          remainingQuantity: null,
        },
      })
      const spy = vi.fn().mockResolvedValue({ id: 1 })
      mutationFnMock.mockImplementation(spy)
      renderForm('/sales/register?subscriptionId=1')
      await waitFor(() => expect(screen.getByLabelText(/^배정수량/)).toBeInTheDocument())
      await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('10,000'))
      await fill(screen.getByLabelText(/^배정수량/), '25')
      await fill(screen.getByLabelText('매도수량'), '25')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))
      await waitFor(() => expect(spy).toHaveBeenCalled())
      expect(spy.mock.calls[0][0]).toMatchObject({
        subscriptionId: 1,
        allocatedQuantity: 25,
        ipoId: 10,
        investorId: 1,
      })
    })

    it('남은 수량이 있는 청약이면 배정수량 입력을 요구하지 않는다', async () => {
      await setupForm()
      expect(screen.queryByLabelText(/^배정수량/)).not.toBeInTheDocument()
    })
  })

  describe('실현손익·최종수익금 계산', () => {
    it('처음에는 공모가로 팔았다고 보고 실현손익 0, 최종수익금은 공모주수수료만큼 마이너스다', async () => {
      await setupForm()
      expect(value('실현손익')).toBe('0')
      expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('-2,000원')
    })

    it('매도단가를 바꾸면 실현손익이 (매도단가 − 공모가) × 수량으로 다시 계산된다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      // (15000 − 10000) × 30 = 150000
      expect(value('실현손익')).toBe('150000')
      expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('148,000원')
    })

    it('세금 및 기타 수수료를 넣으면 실현손익에서 빼고, 공모주수수료는 최종수익금에서만 뺀다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1500')
      expect(value('실현손익')).toBe('148500')
      // 최종수익금 = 148500 − 2000
      expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('146,500원')
    })

    it('증권사 앱의 실현손익을 직접 넣으면 세금 및 기타 수수료가 거꾸로 계산된다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('실현손익'), '148500')
      expect(value('세금 및 기타 수수료 (선택)')).toBe('1500')
      expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('146,500원')
    })

    it('실현손익을 직접 넣은 뒤 매도단가를 고쳐도 실현손익은 유지되고 세금 및 기타가 다시 계산된다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('실현손익'), '148500')
      await fill(screen.getByLabelText('매도단가'), '15100')
      expect(value('실현손익')).toBe('148500')
      // (15100 − 10000) × 30 − 148500 = 4500
      expect(value('세금 및 기타 수수료 (선택)')).toBe('4500')
    })

    it('손실이면 최종수익금이 마이너스로 표시된다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '9000')
      // (9000 − 10000) × 30 = −30000, 최종 = −30000 − 2000
      expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('-32,000원')
    })

    it('실현손익이 (매도단가×수량 − 공모가×수량)보다 크면 오류를 보여 준다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('실현손익'), '151000')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(
          screen.getByText('실현손익이 (매도단가×수량 − 공모가×수량)보다 클 수 없습니다.'),
        ).toBeInTheDocument()
      })
    })
  })

  describe('저장 값', () => {
    it('매도단가×수량을 금액으로, 공모주수수료를 수수료로, 세금 및 기타를 세금으로 보낸다', async () => {
      const spy = vi.fn().mockResolvedValue({ id: 1 })
      mutationFnMock.mockImplementation(spy)
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1500')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => expect(spy).toHaveBeenCalled())
      expect(spy.mock.calls[0][0]).toEqual({
        subscriptionId: 1,
        ipoId: 10,
        investorId: 1,
        quantity: 30,
        price: 15000,
        amount: 450000,
        saleDate: '2026-10-23T09:00',
        fee: 2000,
        tax: 1500,
      })
    })

    it('증권사 실현손익을 넣고 세금 및 기타를 비워 두면 거꾸로 계산한 값이 세금으로 저장된다', async () => {
      const spy = vi.fn().mockResolvedValue({ id: 1 })
      mutationFnMock.mockImplementation(spy)
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('실현손익'), '148500')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => expect(spy).toHaveBeenCalled())
      expect(spy.mock.calls[0][0]).toMatchObject({ price: 15000, fee: 2000, tax: 1500 })
    })
  })

  describe('필수·형식 검사', () => {
    it('매도수량 0 입력 시 에러 메시지 표시 (AC-002)', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도수량'), '0')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('매도수량 음수 입력 시 에러 메시지 표시', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도수량'), '-1')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('매도수량을 비우면 필수 오류가 보인다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도수량'), '')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('매도수량을 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('매도단가를 비우면 필수 오류가 보인다', async () => {
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('매도단가를 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('세금 및 기타 수수료 음수 입력 시 에러 메시지 표시', async () => {
      await setupForm()
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '-1')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('0 이상 정수를 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('배정이 필요한데 배정수량을 비우면 배정수량 오류가 보인다', async () => {
      defaultMock({ detail: { ...ALLOCATION_DETAIL, allocation: null, remainingQuantity: null } })
      renderForm('/sales/register?subscriptionId=1')
      await waitFor(() => expect(screen.getByLabelText(/^배정수량/)).toBeInTheDocument())
      await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('10,000'))
      await fill(screen.getByLabelText('매도수량'), '10')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('배정수량을 입력해 주세요.')).toBeInTheDocument()
      })
    })

    it('공모가·배정총액 모두 없으면 취득금액 계산 불가 → 실현손익 입력 시 세금 필수 안내', async () => {
      defaultMock({
        ipo: { ...IPO, fixedPrice: null },
        detail: {
          ...ALLOCATION_DETAIL,
          ipoFixedPrice: null,
          allocation: { ...ALLOCATION_DETAIL.allocation, allocatedAmount: null },
        },
      })
      renderForm('/sales/register?subscriptionId=1')
      await waitFor(() => expect(screen.getByText('홍길동')).toBeInTheDocument())
      await waitFor(() => expect(screen.getByLabelText('매도수량')).toHaveValue(30))
      // 매도단가 기본값이 비어 있음
      expect(value('매도단가')).toBe('')
      // 매도단가를 비운 상태로 실현손익만 입력 → 취득금액 계산 불가 상태에서 세금 필수 에러
      await fill(screen.getByLabelText('실현손익'), '100000')
      // lastEdited 상태가 'profit'으로 갱신되도록 React 업데이트 플러시 대기
      await new Promise((r) => setTimeout(r, 100))
      // 취득금액 계산 불가 상태에서 실현손익만 넣으면 세금 필수 에러
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(
          screen.getByText(/취득금액을 계산할 수 없어 세금 및 기타 수수료를 직접 입력해 주세요/),
        ).toBeInTheDocument()
      })
      // 매도단가와 세금을 넣으면 제출 가능
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '350000')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(mutationFnMock).toHaveBeenCalled()
      })
    })
  })

  describe('서버 응답', () => {
    it('등록 성공 시 /investments로 이동 (AC-001)', async () => {
      mutationFnMock.mockResolvedValue({ id: 1 })
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/investments')
      })
    })

    it('404 에러 시 "배정을 찾을 수 없습니다." 표시 (AC-004)', async () => {
      mutationFnMock.mockRejectedValue(new ApiError(404, 'Request failed: 404'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('배정을 찾을 수 없습니다.')).toBeInTheDocument()
      })
    })

    it('409 에러 시 원본 메시지 표시 (AC-005)', async () => {
      mutationFnMock.mockRejectedValue(new ApiError(409, '<상세>', '<상세>'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('<상세>')).toBeInTheDocument()
      })
    })

    it('400 에러 시 "입력값을 확인해 주세요." 표시 (AC-006)', async () => {
      mutationFnMock.mockRejectedValue(new ApiError(400, 'Request failed: 400'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
      })
    })

    it('네트워크 오류 시 "네트워크 연결을 확인해 주세요." 표시 (AC-021)', async () => {
      mutationFnMock.mockRejectedValue(new Error('Failed to fetch'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
      })
    })
  })

  it('캡처를 올리면 매칭되는 연결에 값을 채우고 "캡처에서 채움"을 표시한다(저장은 하지 않는다)', async () => {
    defaultMock()
    renderForm('/sales/register?subscriptionId=1')
    const input = await screen.findByLabelText(/캡처 \(여러 장 가능\)/)
    await waitFor(() => expect(input).toBeEnabled())
    await userEvent.upload(input, new File(['x'], 'a.png', { type: 'image/png' }))

    await waitFor(() => expect(screen.getByLabelText('매도수량')).toHaveValue(30))
    expect(screen.getByLabelText('매도단가')).toHaveValue('12,000')
    expect(screen.getByLabelText('매도일')).toHaveValue('2026-10-25')
    expect(screen.getByLabelText(/세금 및 기타 수수료/)).toHaveValue('25')
    expect(screen.getByLabelText('실현손익')).toHaveValue('59,975')
    expect(screen.getAllByText('캡처에서 채움').length).toBeGreaterThanOrEqual(4)
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})

describe('상장일 시초가로 매도단가 기본값(AC-2105~2108)', () => {
  beforeEach(() => {
    localStorage.setItem('currentInvestorId', '1')
    mockNavigate.mockClear()
    mutationFnMock.mockReset()
    mutationFnMock.mockResolvedValue({})
  })

  it('시초가가 있으면 매도단가 기본값이 시초가, 안내 문구에 "상장일 시초가" (AC-2105)', async () => {
    defaultMock({
      ipo: { ...IPO, listingOpenPrice: 15000 },
      detail: ALLOCATION_DETAIL,
    })
    renderForm('/sales/register?subscriptionId=1')
    await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('15,000'))
    expect(value('매도단가')).toBe('15000')
    expect(
      screen.getByText('상장일 시초가 15,000원이 먼저 들어갑니다. 실제 체결가로 고쳐 주세요.'),
    ).toBeInTheDocument()
  })

  it('시초가가 없으면 기존 공모가 규칙(AC-2106)', async () => {
    defaultMock({
      ipo: { ...IPO, listingOpenPrice: null },
    })
    await setupForm()
    expect(value('매도단가')).toBe('10000')
    expect(screen.getByText('공모가 10,000원이 먼저 들어갑니다.')).toBeInTheDocument()
  })

  it('시초가가 0이면 공모가로 돌아간다 (AC-2106)', async () => {
    defaultMock({
      ipo: { ...IPO, listingOpenPrice: 0 },
    })
    await setupForm()
    expect(value('매도단가')).toBe('10000')
    expect(screen.getByText('공모가 10,000원이 먼저 들어갑니다.')).toBeInTheDocument()
  })

  it('시초가가 있어도 실현손익 계산 기준은 공모가(기존 동작 유지) (AC-2108, d)', async () => {
    // 공모가 10000, 시초가 15000, 수량 30 → 실현손익 기본값 (15000-10000)*30 = 150000 (세금 0 기준)
    defaultMock({
      ipo: { ...IPO, listingOpenPrice: 15000 },
    })
    renderForm('/sales/register?subscriptionId=1')
    await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('15,000'))
    expect(value('매도단가')).toBe('15000')
    expect(value('실현손익')).toBe('150000')
    expect(screen.getByRole('group', { name: '수익 요약' })).toHaveTextContent('148,000원')
  })

  it('사용자가 이미 고친 매도단가는 덮어쓰지 않는다 (AC-2107, e)', async () => {
    defaultMock({
      ipo: { ...IPO, listingOpenPrice: 15000 },
    })
    renderForm('/sales/register?subscriptionId=1')
    await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('15,000'))
    // 사용자가 이미 고친 값(12000)을 넣고 다시 렌더링하지 않으므로 덮어쓰지 않아야 함
    await fill(screen.getByLabelText('매도단가'), '12000')
    await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveDisplayValue('12,000'))
  })
})

describe('필수 조회 상태와 등록 차단', () => {
  beforeEach(() => {
    localStorage.setItem('currentInvestorId', '1')
    mockNavigate.mockClear()
    mutationFnMock.mockReset()
    mutationFnMock.mockResolvedValue({})
    defaultMock()
  })

  const SECOND_ACCOUNT = {
    id: 2,
    investorId: 1,
    securitiesFirmId: 1,
    securitiesFirmName: '테스트증권',
    accountNumber: null,
    accountName: '둘째계좌',
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  }

  it('청약·배정 연결 확인에 실패하면 등록 버튼을 비활성화하고 안내한다', async () => {
    defaultMock({
      insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]),
      lookupError: new ApiError(500, 'Request failed: 500'),
    })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(spy).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('청약·배정 연결을 확인하지 못했습니다.')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('청약·배정 연결을 확인하는 중에는 등록할 수 없다', async () => {
    defaultMock({
      insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]),
      lookupPending: true,
    })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })
    expect(await screen.findByText('청약·배정 연결을 확인하는 중입니다…')).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  it('연결 상세를 확인하는 중에는 등록할 수 없다', async () => {
    defaultMock({ detailPending: true })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?subscriptionId=1')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })
    expect(
      await screen.findByText('연결된 청약·배정 정보를 확인하는 중입니다…'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(spy).not.toHaveBeenCalled()
  })

  it('공모주 정보를 확인하는 중에는 등록할 수 없다', async () => {
    defaultMock({ ipoPending: true })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })
    expect(await screen.findByText('공모주 정보를 확인하는 중입니다…')).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  it('공모주 주관사 정보 확인에 실패하면 등록할 수 없다', async () => {
    defaultMock({ insightError: new ApiError(500, 'insight failed') })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })
    expect(screen.getByRole('alert')).toHaveTextContent('공모주 주관사 정보를 확인하지 못했습니다.')
    expect(spy).not.toHaveBeenCalled()
  })

  it('문맥이 확정되기 전에는 캡처를 적용하지 않고 안내만 표시한다', async () => {
    defaultMock({ detailPending: true })
    renderForm('/sales/register?subscriptionId=1')
    const input = await screen.findByLabelText(/캡처 \(여러 장 가능\)/)
    await waitFor(() => expect(input).toBeEnabled())
    await userEvent.upload(input, new File(['x'], 'a.png', { type: 'image/png' }))
    expect(
      await screen.findByText('공모주 정보를 확인하는 중입니다. 잠시 후 다시 시도해 주세요.'),
    ).toBeInTheDocument()
    expect((screen.getByLabelText('매도수량') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('매도단가') as HTMLInputElement).value).toBe('')
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
  })

  it('같은 종목이라도 다른 계좌의 청약은 자동으로 바꾸지 않고 현재 계좌 기준으로 채운다', async () => {
    defaultMock({
      insight: insightWith([{ name: '테스트증권', role: '주관', shares: 1 }]),
      accounts: [SECURITIES_ACCOUNTS[0], SECOND_ACCOUNT],
      lookup: EMPTY_HISTORY_PAGE,
    })
    const spy = vi.fn().mockResolvedValue({ id: 1 })
    mutationFnMock.mockImplementation(spy)
    renderForm('/sales/register?ipoId=10')
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /둘째계좌/ })).toBeInTheDocument(),
    )
    await fill(screen.getByLabelText('증권계좌 *'), '2')
    await waitFor(() => expect(screen.getByLabelText('증권계좌 *')).toHaveValue('2'))
    // 캡처 기록은 계좌 1의 청약에 매칭된다(종목코드 동일)
    const input = await screen.findByLabelText(/캡처 \(여러 장 가능\)/)
    await waitFor(() => expect(input).toBeEnabled())
    await userEvent.upload(input, new File(['x'], 'a.png', { type: 'image/png' }))
    await waitFor(() => expect(screen.getByLabelText('매도수량')).toHaveValue(30))
    expect(screen.getByText(/다른 계좌의 청약에 대응합니다/)).toBeInTheDocument()
    // 현재 계좌(2) 기준으로 남기고, 새 연결을 만든다
    await fill(screen.getByLabelText(/^배정수량/), '10')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() => expect(spy).toHaveBeenCalled())
    const payload = spy.mock.calls[0][0]
    expect(payload.securitiesAccountId).toBe(2)
    expect(payload.allocatedQuantity).toBe(10)
    expect(payload).not.toHaveProperty('subscriptionId')
    expect(payload).not.toHaveProperty('allocationId')
  })
})
