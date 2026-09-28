import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import CostRegisterForm from './CostRegisterForm.tsx'
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

vi.mock('../useCostRegister.ts', () => ({
  useCostRegister: () => {
    const queryClient = useQueryClient()
    return useMutation({
      mutationFn: mutationFnMock,
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['costs'] })
        mockNavigate('/investments')
      },
    })
  },
}))

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

function defaultMock() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    const p = String(path)
    if (p.startsWith('/investment-history') && (!options || options.method === 'GET'))
      return SUBSCRIPTION_HISTORY
    throw new Error(`unexpected path: ${String(path)}`)
  })
}

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/costs/register']}>
        <CostRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function setupForm() {
  renderForm()
  await waitFor(() => {
    expect(screen.getByLabelText('청약')).not.toBeDisabled()
  })
  fireEvent.change(screen.getByLabelText('청약'), { target: { value: '1' } })
  await waitFor(() => {
    expect(screen.getByLabelText('비용종류')).not.toBeDisabled()
  })
  fireEvent.change(screen.getByLabelText('비용종류'), { target: { value: '청약수수료' } })
  fireEvent.change(screen.getByLabelText('금액'), { target: { value: '25000' } })
  fireEvent.change(screen.getByLabelText('발생일시'), { target: { value: '2026-09-10T10:00' } })
}

describe('CostRegisterForm', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    mutationFnMock = () => Promise.resolve({})
    defaultMock()
  })

  it('청약 목록을 로드하고 드롭다운에 표시함', async () => {
    renderForm()
    await waitFor(() => {
      const select = screen.getByLabelText('청약')
      expect(select).toHaveDisplayValue('청약 선택')
      expect(select).toContainHTML('카카오뱅크')
    })
  })

  it('청약 미선택 시 필수값 검증', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('청약'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('청약을 선택해 주세요.')).toBeInTheDocument()
    })
  })

  it('비용종류 필수값 검증', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('비용종류'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('비용종류를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('금액 0 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('금액'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('금액 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('금액'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('발생일시 필수값 검증', async () => {
    await setupForm()
    fireEvent.change(screen.getByLabelText('발생일시'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('발생일시를 선택해 주세요.')).toBeInTheDocument()
    })
  })

  it('메모 255자 초과 시 에러 메시지 표시', async () => {
    await setupForm()
    const longMemo = 'a'.repeat(256)
    fireEvent.change(screen.getByLabelText('메모 (선택)'), { target: { value: longMemo } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('메모는 255자 이하로 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('등록 성공 시 costs 쿼리 무효화 및 올바른 subscriptionId 전달', async () => {
    mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(mutationFnMock).toHaveBeenCalledWith(
        expect.objectContaining({
          subscriptionId: 1,
          costType: '청약수수료',
          amount: 25000,
          incurredAt: '2026-09-10T10:00',
        }),
        expect.any(Object),
      )
    })
  })

  it('404 에러 시 "청약을 찾을 수 없습니다." 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 404 Not Found'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('청약을 찾을 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('400 에러 시 서버 메시지 그대로 표시', async () => {
    mutationFnMock = vi
      .fn()
      .mockRejectedValue(new Error('Request failed: 400 Bad Request: amount must be positive'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(
        screen.getByText('Request failed: 400 Bad Request: amount must be positive'),
      ).toBeInTheDocument()
    })
  })

  it('400 에러 시 기본 메시지 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 400 Bad Request'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('Request failed: 400 Bad Request')).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 "네트워크 연결을 확인해 주세요." 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Failed to fetch'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('등록 성공 시 /investments로 이동', async () => {
    mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/investments')
    })
  })

  it('등록 실패 시 이동하지 않음', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Request failed: 400 Bad Request'))
    await setupForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() => {
      expect(screen.getByText('Request failed: 400 Bad Request')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
