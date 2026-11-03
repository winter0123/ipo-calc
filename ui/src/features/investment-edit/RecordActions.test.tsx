import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fill } from '@/test/fill'
import { ApiError } from '@/shared/api/apiError.ts'
import RecordActions, { type RecordKind } from './RecordActions.tsx'

vi.mock('@/entities/allocation/index.ts', async () => {
  const actual = await vi.importActual('@/entities/allocation/index.ts')
  return {
    ...actual,
    getAllocation: vi.fn(),
    updateAllocation: vi.fn(),
    deleteAllocation: vi.fn(),
  }
})

vi.mock('@/entities/sale/index.ts', async () => {
  const actual = await vi.importActual('@/entities/sale/index.ts')
  return {
    ...actual,
    getSale: vi.fn(),
    updateSale: vi.fn(),
    deleteSale: vi.fn(),
  }
})

vi.mock('@/entities/subscription/index.ts', async () => {
  const actual = await vi.importActual('@/entities/subscription/index.ts')
  return {
    ...actual,
    getSubscription: vi.fn(),
    updateSubscription: vi.fn(),
    deleteSubscription: vi.fn(),
    useInvestmentHistoryDetail: vi.fn(),
    investmentHistoryKeys: {
      source: (type: string, id: number) => ['investment-history', type, id],
    },
  }
})

import {
  deleteSubscription,
  getSubscription,
  updateSubscription,
  useInvestmentHistoryDetail,
} from '@/entities/subscription/index.ts'
import { getAllocation, updateAllocation, deleteAllocation } from '@/entities/allocation/index.ts'
import { getSale, updateSale, deleteSale } from '@/entities/sale/index.ts'

const mockDeleteSubscription = vi.mocked(deleteSubscription)
const mockDeleteAllocation = vi.mocked(deleteAllocation)
const mockDeleteSale = vi.mocked(deleteSale)
const mockGetSubscription = vi.mocked(getSubscription)
const mockUpdateSubscription = vi.mocked(updateSubscription)
const mockGetSale = vi.mocked(getSale)
const mockUpdateSale = vi.mocked(updateSale)
const mockGetAllocation = vi.mocked(getAllocation)
const mockUpdateAllocation = vi.mocked(updateAllocation)
const mockUseInvestmentHistoryDetail = vi.mocked(useInvestmentHistoryDetail)

const RECORDS: Record<RecordKind, object> = {
  subscription: {
    id: 1,
    investorId: 1,
    securitiesAccountId: 1,
    ipoId: 1,
    quantity: 10,
    depositAmount: 200000,
    subscribedAt: '2026-09-01T09:00:00',
    memo: '메모',
    status: 'ACTIVE',
    createdAt: '2026-09-01T09:00:00',
    updatedAt: '2026-09-01T09:00:00',
  },
  allocation: {
    id: 2,
    subscriptionId: 1,
    allocatedQuantity: 5,
    allocatedAt: '2026-09-05T09:00:00',
    allocatedAmount: 50000,
    allocationMethod: 'LOTTERY',
    note: '',
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-09-05T09:00:00',
    updatedAt: '2026-09-05T09:00:00',
  },
  sale: {
    id: 3,
    allocationId: 2,
    quantity: 4,
    price: 15000,
    amount: 60000,
    saleDate: '2026-09-10T09:00:00',
    fee: 2000,
    tax: 300,
    otherCost: null,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-09-10T09:00:00',
    updatedAt: '2026-09-10T09:00:00',
  },
}

const SUB_DETAIL_FOR_ALLOCATION = {
  id: 1,
  subscribedAt: '2026-09-01T09:00:00Z',
  ipoId: 1,
  ipoName: '테스트 공모주',
  ipoFixedPrice: 10000,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: { id: 2, allocatedQuantity: 5, allocatedAmount: 50000, allocationStatus: 'ACTIVE' },
  sales: [],
  remainingQuantity: 5,
  investorName: '홍길동',
  securitiesFirmName: '테스트증권',
  securitiesAccountId: 1,
  securitiesAccountName: '테스트계좌',
}

const ID = { subscription: 1, allocation: 2, sale: 3 }

function setup(kind: RecordKind) {
  // 개별 API 함수 mock 설정 (dialog query에서 사용)
  mockGetSubscription.mockResolvedValue(
    RECORDS.subscription as Awaited<ReturnType<typeof getSubscription>>,
  )
  mockGetAllocation.mockResolvedValue(
    RECORDS.allocation as Awaited<ReturnType<typeof getAllocation>>,
  )
  mockGetSale.mockResolvedValue(RECORDS.sale as Awaited<ReturnType<typeof getSale>>)
  mockUpdateSubscription.mockResolvedValue({ ...RECORDS.subscription, quantity: 20 } as Awaited<
    ReturnType<typeof updateSubscription>
  >)
  mockUpdateAllocation.mockResolvedValue({
    ...RECORDS.allocation,
    allocatedQuantity: 60,
  } as Awaited<ReturnType<typeof updateAllocation>>)
  mockUpdateSale.mockResolvedValue({ ...RECORDS.sale, quantity: 2 } as Awaited<
    ReturnType<typeof updateSale>
  >)
  if (kind === 'allocation') {
    mockUseInvestmentHistoryDetail.mockReturnValue({
      data: SUB_DETAIL_FOR_ALLOCATION,
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
  } else if (kind === 'subscription') {
    mockUseInvestmentHistoryDetail.mockReturnValue({
      data: null,
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
  } else if (kind === 'sale') {
    mockUseInvestmentHistoryDetail.mockReturnValue({
      data: null,
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
  }
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RecordActions kind={kind} id={ID[kind]} label="테스트 항목" />
    </QueryClientProvider>,
  )
}

type PatchCall = [unknown, unknown]

const call = (method: string, kind?: RecordKind): PatchCall | number | undefined => {
  if (method === 'DELETE' && kind) {
    const mockDelete =
      kind === 'subscription'
        ? mockDeleteSubscription
        : kind === 'allocation'
          ? mockDeleteAllocation
          : mockDeleteSale
    const call = mockDelete.mock.calls[0]
    return call ? call[0] : undefined
  }
  if (method === 'PATCH' && kind) {
    const mockUpdate =
      kind === 'subscription'
        ? mockUpdateSubscription
        : kind === 'allocation'
          ? mockUpdateAllocation
          : mockUpdateSale
    const call = mockUpdate.mock.calls[0]
    return call ? [call[0], call[1]] : undefined
  }
  return undefined
}

describe('RecordActions', () => {
  beforeEach(() => {
    mockDeleteSubscription.mockReset()
    mockDeleteAllocation.mockReset()
    mockDeleteSale.mockReset()
    mockUpdateSubscription.mockReset()
    mockUpdateAllocation.mockReset()
    mockUpdateSale.mockReset()
    mockUseInvestmentHistoryDetail.mockReset()
  })

  it('청약 수정: 바뀐 값을 PATCH로 보낸다', async () => {
    setup('subscription')
    await userEvent.click(screen.getByRole('button', { name: '청약 수정' }))
    await fill(await screen.findByLabelText('청약수량'), '20')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH', 'subscription')).toBeDefined())
    const patchCall = call('PATCH', 'subscription') as PatchCall
    const [, data] = patchCall
    expect(data).toEqual({
      quantity: 20,
      depositAmount: 200000,
      subscribedAt: '2026-09-01T09:00',
      memo: '메모',
    })
  })

  it('배정 수정: 배정수량과 배정방식을 PATCH로 보낸다', async () => {
    setup('allocation')
    await userEvent.click(screen.getByRole('button', { name: '배정 수정' }))
    await fill(await screen.findByLabelText('배정수량'), '3')
    await fill(screen.getByLabelText('배정방식 (선택)'), 'PROPORTIONAL')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH', 'allocation')).toBeDefined())
    const patchCall = call('PATCH', 'allocation') as PatchCall
    const [, data] = patchCall
    expect(data).toEqual({
      allocatedQuantity: 3,
      allocationMethod: 'PROPORTIONAL',
      note: '',
    })
  })

  it('매도 수정: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타로 보낸다', async () => {
    setup('sale')
    await userEvent.click(screen.getByRole('button', { name: '매도 수정' }))
    await fill(await screen.findByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '20000')
    await fill(screen.getByLabelText('세금 및 기타'), '500')
    await fill(screen.getByLabelText('공모주수수료'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH', 'sale')).toBeDefined())
    const patchCall = call('PATCH', 'sale') as PatchCall
    const [, data] = patchCall
    expect(data).toEqual({
      quantity: 2,
      price: 20000,
      amount: 40000,
      saleDate: '2026-09-10T09:00',
      fee: 1000,
      tax: 500,
    })
  })

  it.each(['subscription', 'allocation', 'sale'] as const)(
    '%s 삭제: 확인 후 DELETE를 호출한다',
    async (kind) => {
      setup(kind)
      await userEvent.click(screen.getByRole('button', { name: /삭제$/ }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
      )

      const expectedId = kind === 'subscription' ? 1 : kind === 'allocation' ? 2 : 3
      const mockDelete =
        kind === 'subscription'
          ? mockDeleteSubscription
          : kind === 'allocation'
            ? mockDeleteAllocation
            : mockDeleteSale
      await waitFor(() => expect(mockDelete).toHaveBeenCalledWith(expectedId, expect.any(Object)))
    },
  )

  it('삭제 확인을 연달아 눌러도 DELETE는 한 번만 나간다', async () => {
    let release: () => void = () => {}
    const mockDeleteSaleLocal = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          release = () => resolve()
        }),
    )
    mockDeleteSale.mockImplementation(mockDeleteSaleLocal)
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RecordActions kind="sale" id={3} />
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: '매도 삭제' }))
    const confirm = within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' })
    await userEvent.dblClick(confirm)
    await userEvent.click(confirm)
    release()

    await waitFor(() => expect(mockDeleteSaleLocal).toHaveBeenCalledTimes(1))
  })

  it('배정 수정에는 배정일·배정단가 입력이 없고 배정 총액은 읽기 전용이다', async () => {
    setup('allocation')
    await userEvent.click(screen.getByRole('button', { name: /수정$/ }))

    expect(await screen.findByLabelText('배정 총액')).toHaveTextContent('50,000원')
    expect(screen.queryByLabelText('배정일')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/배정단가/)).not.toBeInTheDocument()
  })

  it('삭제가 409면 서버 메시지를 그대로 보여 준다', async () => {
    const message = '매도 기록이 있는 배정은 취소할 수 없습니다. 매도를 먼저 삭제해 주세요.'
    mockDeleteAllocation.mockRejectedValueOnce(new ApiError(409, message, message))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RecordActions kind="allocation" id={2} />
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: '배정 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })
})
