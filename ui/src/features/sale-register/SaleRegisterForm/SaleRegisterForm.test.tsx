import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SaleRegisterForm from './SaleRegisterForm.tsx'
import { apiFetch } from '../../../shared/api/client.ts'

vi.mock('../../../shared/api/client.ts', () => ({
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
  { id: 1, displayName: '홍길동', accountId: 1, status: 'ACTIVE', deletedAt: null, createdAt: '2026-01-01', updatedAt: '2026-01-01' },
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
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
}

const ALLOCATION_DETAIL = {
  id: 1,
  subscribedAt: '2026-09-01T09:00:00Z',
  ipoId: 10,
  ipoName: '카카오뱅크',
  ipoFixedPrice: 39000,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: { id: 1, allocatedQuantity: 50, allocatedAt: null, allocationMethod: null, allocationPrice: null, note: null, status: 'PENDING', createdAt: '2026-01-01', updatedAt: '2026-01-01' },
  sales: [],
  remainingQuantity: 50,
}

function defaultMock() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    const p = String(path)
    if (p === '/investors' && (!options || options.method === 'GET')) return INVESTORS
    if (p === '/investment-history/1' && (!options || options.method === 'GET')) return ALLOCATION_DETAIL
    if (p.startsWith('/investment-history') && (!options || options.method === 'GET')) return SUBSCRIPTION_HISTORY
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

async function setupForm() {
  renderForm()
  await waitFor(() => {
    expect(screen.getByText('홍길동')).toBeInTheDocument()
  })
  fireEvent.change(screen.getByLabelText('투자자'), { target: { value: '1' } })
  await waitFor(() => {
    expect(screen.getByLabelText('청약')).not.toBeDisabled()
  })
  fireEvent.change(screen.getByLabelText('청약'), { target: { value: '1' } })
  await waitFor(() => {
    expect(screen.getByText('남은 수량 (참고)')).toBeInTheDocument()
  })
  await waitFor(() => {
    expect(screen.getByLabelText('배정')).not.toBeDisabled()
  })
  fireEvent.change(screen.getByLabelText('배정'), { target: { value: '1' } })
  await waitFor(() => {
    expect(screen.getByLabelText('매도수량')).not.toBeDisabled()
  })
  fireEvent.change(screen.getByLabelText('매도수량'), { target: { value: '10' } })
  fireEvent.change(screen.getByLabelText('매도일시'), { target: { value: '2026-09-21T10:00' } })
}

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

  it('매도수량 0 입력 시 에러 메시지 표시 (AC-002)', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('매도수량'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('매도수량 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('매도수량'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('단가 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('단가 (선택)'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 이상 정수를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('등록 성공 시 /investments로 이동 (AC-001)', async () => {
    mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/investments')
    })
  })

  it('404 에러 시 "배정을 찾을 수 없습니다." 표시 (AC-004)', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 404 Not Found'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('배정을 찾을 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('409 에러 시 원본 메시지 표시 (AC-005)', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 409 Conflict: <상세>'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('Request failed: 409 Conflict: <상세>')).toBeInTheDocument()
    })
  })

  it('400 에러 시 "입력값을 확인해 주세요." 표시 (AC-006)', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 400 Bad Request'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 "네트워크 연결을 확인해 주세요." 표시 (AC-021)', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Failed to fetch'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

})
