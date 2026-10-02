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

let mutationFnMock: (data: unknown) => Promise<unknown> = () => Promise.resolve({})

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

const SUBSCRIPTION_HISTORY = {
  content: [
    {
      id: 1,
      subscribedAt: '2026-09-01T09:00:00Z',
      ipoId: 10,
      ipoName: '카카오뱅크',
      ipoFixedPrice: 39000,
      subscriptionQuantity: 100,
      subscriptionStatus: 'ACTIVE',
      allocation: null,
      sales: [],
      remainingQuantity: 50,
      investorName: null,
      securitiesFirmName: null,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
}

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
    allocationPrice: null,
    allocationStatus: 'ACTIVE',
  },
  sales: [],
  remainingQuantity: 30,
  investorName: null,
  securitiesFirmName: null,
}

function defaultMock(overrides: { ipo?: unknown; detail?: unknown } = {}) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    const p = String(path)
    const isGet = !options || options.method === 'GET' || options.method === undefined
    if (p === '/investors' && isGet) return INVESTORS
    if (p === '/ipos/10' && isGet) return overrides.ipo ?? IPO
    if (p === '/investment-history/1' && isGet) return overrides.detail ?? ALLOCATION_DETAIL
    if (p.startsWith('/investment-history') && isGet) return SUBSCRIPTION_HISTORY
    throw new Error(`unexpected path: ${String(path)}`)
  })
}

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/sales/register']}>
        <SaleRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** 투자자 → 청약 → 배정을 고르고 기본값(공모가·상장일·수량)이 채워질 때까지 기다린다. */
async function setupForm() {
  renderForm()
  await waitFor(() => {
    expect(screen.getByText('홍길동')).toBeInTheDocument()
  })
  await fill(screen.getByLabelText('투자자'), '1')
  await waitFor(() => {
    expect(screen.getByLabelText('청약')).not.toBeDisabled()
  })
  await fill(screen.getByLabelText('청약'), '1')
  await waitFor(() => {
    expect(screen.getByText('남은 수량 (참고)')).toBeInTheDocument()
  })
  await waitFor(() => {
    expect(screen.getByLabelText('배정')).not.toBeDisabled()
  })
  await fill(screen.getByLabelText('배정'), '1')
  await waitFor(() => {
    expect(screen.getByLabelText('매도단가')).toHaveValue(10000)
  })
}

const value = (label: string) => (screen.getByLabelText(label) as HTMLInputElement).value

describe('SaleRegisterForm', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    mutationFnMock = () => Promise.resolve({})
    defaultMock()
  })

  it('투자자 목록을 로드하고 드롭다운에 표시함', async () => {
    renderForm()
    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument()
    })
  })

  it('참고 표시가 렌더링됨 (AC-018)', async () => {
    await setupForm()
    expect(screen.getByText('남은 수량 (참고)')).toBeInTheDocument()
  })

  describe('기본값 자동 입력', () => {
    it('매도일는 해당 공모주 상장일, 매도단가는 공모가, 매도수량은 남은 수량이 먼저 들어간다', async () => {
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

    it('상장일이 없으면 매도일는 비어 있다', async () => {
      defaultMock({ ipo: { ...IPO, listingDate: null } })
      await setupForm()
      expect(value('매도일')).toBe('')
    })

    it('확정 공모가가 없으면 배정단가를 매도단가로 쓴다', async () => {
      defaultMock({
        ipo: { ...IPO, fixedPrice: null },
        detail: {
          ...ALLOCATION_DETAIL,
          ipoFixedPrice: null,
          allocation: { ...ALLOCATION_DETAIL.allocation, allocationPrice: 9500 },
        },
      })
      renderForm()
      await waitFor(() => expect(screen.getByText('홍길동')).toBeInTheDocument())
      await fill(screen.getByLabelText('투자자'), '1')
      await waitFor(() => expect(screen.getByLabelText('청약')).not.toBeDisabled())
      await fill(screen.getByLabelText('청약'), '1')
      await waitFor(() => expect(screen.getByLabelText('매도단가')).toHaveValue(9500))
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
      mutationFnMock = spy
      await setupForm()
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1500')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => expect(spy).toHaveBeenCalled())
      expect(spy.mock.calls[0][0]).toEqual({
        allocationId: 1,
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
      mutationFnMock = spy
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

    it('공모가를 몰라 단가가 비어 있고 실현손익만 넣으면 세금 및 기타를 직접 넣으라고 안내한다', async () => {
      defaultMock({
        ipo: { ...IPO, fixedPrice: null },
        detail: { ...ALLOCATION_DETAIL, ipoFixedPrice: null },
      })
      renderForm()
      await waitFor(() => expect(screen.getByText('홍길동')).toBeInTheDocument())
      await fill(screen.getByLabelText('투자자'), '1')
      await waitFor(() => expect(screen.getByLabelText('청약')).not.toBeDisabled())
      await fill(screen.getByLabelText('청약'), '1')
      await waitFor(() => expect(screen.getByLabelText('매도수량')).toHaveValue(30))
      expect(value('매도단가')).toBe('')
      await fill(screen.getByLabelText('매도단가'), '15000')
      await fill(screen.getByLabelText('실현손익'), '100000')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(
          screen.getByText('공모가를 알 수 없어 세금 및 기타 수수료를 직접 입력해 주세요.'),
        ).toBeInTheDocument()
      })
    })
  })

  describe('서버 응답', () => {
    it('등록 성공 시 /investments로 이동 (AC-001)', async () => {
      mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/investments')
      })
    })

    it('404 에러 시 "배정을 찾을 수 없습니다." 표시 (AC-004)', async () => {
      mutationFnMock = vi.fn().mockRejectedValue(new ApiError(404, 'Request failed: 404'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('배정을 찾을 수 없습니다.')).toBeInTheDocument()
      })
    })

    it('409 에러 시 원본 메시지 표시 (AC-005)', async () => {
      mutationFnMock = vi.fn().mockRejectedValue(new ApiError(409, '<상세>', '<상세>'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('<상세>')).toBeInTheDocument()
      })
    })

    it('400 에러 시 "입력값을 확인해 주세요." 표시 (AC-006)', async () => {
      mutationFnMock = vi.fn().mockRejectedValue(new ApiError(400, 'Request failed: 400'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
      })
    })

    it('네트워크 오류 시 "네트워크 연결을 확인해 주세요." 표시 (AC-021)', async () => {
      mutationFnMock = vi.fn().mockRejectedValue(new Error('Failed to fetch'))
      await setupForm()
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
      })
    })
  })
})
