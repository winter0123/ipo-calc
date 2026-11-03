import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AllocationEditDialog } from './AllocationEditDialog.tsx'

vi.mock('@/entities/allocation/index.ts', () => ({
  getAllocation: vi.fn(),
  updateAllocation: vi.fn(),
}))

vi.mock('@/entities/subscription/index.ts', () => ({
  useInvestmentHistoryDetail: vi.fn(),
  investmentHistoryKeys: {
    source: (type: string, id: number) => ['investment-history', type, id],
  },
}))

import { getAllocation, updateAllocation } from '@/entities/allocation/index.ts'
import { useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'

const mockGetAllocation = vi.mocked(getAllocation)
const mockUpdateAllocation = vi.mocked(updateAllocation)
const mockUseInvestmentHistoryDetail = vi.mocked(useInvestmentHistoryDetail)

const ALLOCATION_RECORD = {
  id: 1,
  subscriptionId: 10,
  allocatedQuantity: 50,
  allocatedAt: '2026-10-01T09:00:00Z',
  allocationMethod: 'PROPORTIONAL',
  allocatedAmount: 500000,
  note: '테스트 비고',
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
}

const SUB_DETAIL_WITH_FIXED_PRICE = {
  id: 1,
  subscribedAt: '2026-10-01T09:00:00Z',
  ipoId: 10,
  ipoName: '테스트 공모주',
  ipoFixedPrice: 10000,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000, allocationStatus: 'ACTIVE' },
  sales: [],
  remainingQuantity: 30,
  investorName: '홍길동',
  securitiesFirmName: '테스트증권',
  securitiesAccountId: 1,
  securitiesAccountName: '테스트계좌',
}

const SUB_DETAIL_NO_FIXED_PRICE = {
  id: 1,
  subscribedAt: '2026-10-01T09:00:00Z',
  ipoId: 10,
  ipoName: '테스트 공모주',
  ipoFixedPrice: null,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000, allocationStatus: 'ACTIVE' },
  sales: [],
  remainingQuantity: 30,
  investorName: '홍길동',
  securitiesFirmName: '테스트증권',
  securitiesAccountId: 1,
  securitiesAccountName: '테스트계좌',
}

function renderDialog(
  allocation = ALLOCATION_RECORD,
  subDetail:
    | typeof SUB_DETAIL_WITH_FIXED_PRICE
    | typeof SUB_DETAIL_NO_FIXED_PRICE = SUB_DETAIL_WITH_FIXED_PRICE,
) {
  mockGetAllocation.mockResolvedValue(allocation)
  mockUseInvestmentHistoryDetail.mockReturnValue({
    data: subDetail as unknown as typeof SUB_DETAIL_WITH_FIXED_PRICE,
    isLoading: false,
    isPending: false,
    isError: false,
    isSuccess: true,
    error: null,
    failureCount: 0,
    failureReason: null,
    refetch: vi.fn(),
    fetchStatus: 'idle',
    status: 'success',
    dataUpdatedAt: Date.now(),
    errorUpdatedAt: 0,
    isFetched: true,
    isFetching: false,
    isRefetching: false,
    isStale: false,
  } as unknown as ReturnType<typeof useInvestmentHistoryDetail>)
  mockUpdateAllocation.mockResolvedValue({ ...allocation, allocatedQuantity: 60 })

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AllocationEditDialog id={1} onClose={vi.fn()} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AllocationEditDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('배정 총액이 확정 공모가 × 배정수량으로 실시간 표시된다', async () => {
    renderDialog()
    await waitFor(() => {
      expect(screen.getByText('500,000원')).toBeInTheDocument()
    })
  })

  it('배정수량을 변경하면 배정 총액이 실시간으로 갱신된다', async () => {
    renderDialog()
    await waitFor(() => {
      expect(screen.getByText('500,000원')).toBeInTheDocument()
    })

    const quantityInput = screen.getByLabelText('배정수량')
    await userEvent.clear(quantityInput)
    await userEvent.type(quantityInput, '60')

    await waitFor(() => {
      expect(screen.getByText('600,000원')).toBeInTheDocument()
    })
  })

  it('확정 공모가가 없으면 자동 계산 불가 안내가 표시된다', async () => {
    renderDialog(ALLOCATION_RECORD, SUB_DETAIL_NO_FIXED_PRICE)
    await waitFor(() => {
      expect(screen.getByText('확정 공모가가 없어 자동 계산할 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('배정수량 0이면 자동 계산 불가 안내가 표시된다', async () => {
    renderDialog()
    await waitFor(() => {
      expect(screen.getByText('500,000원')).toBeInTheDocument()
    })

    const quantityInput = screen.getByLabelText('배정수량')
    await userEvent.clear(quantityInput)
    await userEvent.type(quantityInput, '0')

    await waitFor(() => {
      expect(screen.getByText('확정 공모가가 없어 자동 계산할 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('저장 시 allocatedAmount, allocatedAt, allocationPrice를 서버로 보내지 않는다', async () => {
    renderDialog()
    await waitFor(() => {
      expect(screen.getByText('500,000원')).toBeInTheDocument()
    })

    const quantityInput = screen.getByLabelText('배정수량')
    await userEvent.clear(quantityInput)
    await userEvent.type(quantityInput, '60')

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      expect(mockUpdateAllocation).toHaveBeenCalledTimes(1)
    })

    const updateCall = mockUpdateAllocation.mock.calls[0][1]
    expect(updateCall).toEqual({
      allocatedQuantity: 60,
      allocationMethod: 'PROPORTIONAL',
      note: '테스트 비고',
    })
    expect(updateCall).not.toHaveProperty('allocatedAmount')
    expect(updateCall).not.toHaveProperty('allocatedAt')
    expect(updateCall).not.toHaveProperty('allocationPrice')
  })

  it('배정방식을 비우려 하면 에러가 표시되고 저장되지 않는다', async () => {
    renderDialog()
    await waitFor(() => {
      expect(screen.getByText('500,000원')).toBeInTheDocument()
    })

    const methodSelect = screen.getByLabelText('배정방식 (선택)')
    await userEvent.selectOptions(methodSelect, '')

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      expect(
        screen.getByText('배정방식은 비울 수 없습니다. 하나를 골라 주세요.'),
      ).toBeInTheDocument()
    })
    expect(mockUpdateAllocation).not.toHaveBeenCalled()
  })
})
