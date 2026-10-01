import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AllocationRegisterForm from './AllocationRegisterForm.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

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

const SUBSCRIPTION_DETAIL = {
  id: 1,
  subscribedAt: '2026-09-01T09:00:00Z',
  ipoId: 10,
  ipoName: '카카오뱅크',
  ipoFixedPrice: 39000,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: null,
}

function mockSubscriptionDetailFetch() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
    if (path === '/investment-history/1' && (!options || options.method === 'GET'))
      return SUBSCRIPTION_DETAIL
    throw new Error(`unexpected path: ${String(path)}`)
  })
}

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/allocations/register?subscriptionId=1']}>
        <AllocationRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForDetailLoaded() {
  await waitFor(() => {
    expect(screen.getByText('청약수량 (참고)')).toBeInTheDocument()
    expect(screen.getByDisplayValue('100')).toBeInTheDocument()
  })
}

function fillValidForm() {
  fireEvent.change(screen.getByLabelText('배정수량'), { target: { value: '50' } })
  fireEvent.change(screen.getByLabelText('배정일시'), { target: { value: '2026-09-21T10:00' } })
  fireEvent.change(screen.getByLabelText('배정방식 (선택)'), { target: { value: 'PROPORTIONAL' } })
  fireEvent.change(screen.getByLabelText('배정단가 (선택)'), { target: { value: '39000' } })
  fireEvent.change(screen.getByLabelText('비고 (선택)'), { target: { value: '테스트 배정' } })
}

describe('AllocationRegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  it('청약 상세 로딩 상태를 표시한 뒤 응답 수신 후 참고 수량을 표시함 (AC-022)', async () => {
    let resolveDetail: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET')) {
        return new Promise((resolve) => {
          resolveDetail = resolve
        })
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    renderForm()

    expect(screen.getByText('청약 정보를 불러오는 중...')).toBeInTheDocument()

    resolveDetail(SUBSCRIPTION_DETAIL)

    await waitForDetailLoaded()

    // ReferenceQuantity는 읽기 전용으로 표시됨
    const referenceInput = screen.getByDisplayValue('100')
    expect(referenceInput).toBeDisabled()
    expect(referenceInput).toHaveAttribute('readOnly')
    expect(referenceInput).toHaveAttribute('aria-readonly', 'true')
  })

  it('정상 배정 등록 성공 시 /subscriptions로 이동 (AC-013)', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        return {
          id: 1,
          subscriptionId: 1,
          allocatedQuantity: 50,
          allocatedAt: '2026-09-21T10:00:00',
          allocationMethod: 'PROPORTIONAL',
          allocationPrice: 39000,
          note: '테스트 배정',
          status: 'CONFIRMED',
          createdAt: '2026-09-21T00:00:00Z',
          updatedAt: '2026-09-21T00:00:00Z',
        }
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/allocations',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    const call = vi
      .mocked(apiFetch)
      .mock.calls.find(([path, opts]) => path === '/allocations' && opts?.method === 'POST')!
    const body = JSON.parse((call[1] as RequestInit).body as string)
    expect(body.subscriptionId).toBe(1)
    expect(body.allocatedQuantity).toBe(50)
    expect(body.allocatedAt).toBe('2026-09-21T10:00')
    expect(body.allocationMethod).toBe('PROPORTIONAL')
    expect(body.allocationPrice).toBe(39000)
    expect(body.note).toBe('테스트 배정')

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })

  it('필수값(배정수량, 배정일시) 미입력 시 클라이언트 사이드 검증 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('배정수량을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('배정일시를 선택해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/allocations',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('배정수량이 0 이하이면 에러 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    fillValidForm()
    fireEvent.change(screen.getByLabelText('배정수량'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/allocations',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('배정단가가 음수이면 에러 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    fillValidForm()
    fireEvent.change(screen.getByLabelText('배정단가 (선택)'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/allocations',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('청약 상세 조회 404 응답 시 에러 메시지 표시 (AC-015)', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(404, '청약을 찾을 수 없습니다', '청약을 찾을 수 없습니다'),
    )
    renderForm()

    await waitFor(() => {
      expect(
        screen.getByText('청약 정보를 불러올 수 없습니다. 청약 목록으로 이동합니다.'),
      ).toBeInTheDocument()
    })
  })

  it('배정 등록 400 응답 시 에러 메시지 표시 (AC-014)', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    // POST /allocations만 에러를 던지도록 설정 (GET /investment-history/1은 mockSubscriptionDetailFetch가 처리)
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        throw new ApiError(400, 'Request failed: 400')
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('중복 배정 409 응답 시 에러 메시지 표시 (AC-016)', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        throw new ApiError(409, 'Request failed: 409')
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('이미 동일한 청약에 대한 배정이 존재합니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('400 응답 시 서버 메시지 그대로 표시 (AC-014)', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        throw new ApiError(400, '유효하지 않은 입력입니다.', '유효하지 않은 입력입니다.')
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('유효하지 않은 입력입니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('네트워크 오류 시 에러 메시지 표시 (AC-021)', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        throw new Error('Failed to fetch')
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('선택 필드(배정방식, 배정단가, 비고)는 비워도 제출 가능', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') {
        return {
          id: 1,
          subscriptionId: 1,
          allocatedQuantity: 50,
          allocatedAt: '2026-09-21T10:00:00',
          allocationMethod: undefined,
          allocationPrice: undefined,
          note: undefined,
          status: 'CONFIRMED',
          createdAt: '2026-09-21T00:00:00Z',
          updatedAt: '2026-09-21T00:00:00Z',
        }
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    // 필수 필드만 입력 (배정방식은 선택하지 않음)
    fireEvent.change(screen.getByLabelText('배정수량'), { target: { value: '50' } })
    fireEvent.change(screen.getByLabelText('배정일시'), { target: { value: '2026-09-21T10:00' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/allocations',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    const call = vi
      .mocked(apiFetch)
      .mock.calls.find(([path, opts]) => path === '/allocations' && opts?.method === 'POST')!
    const body = JSON.parse((call[1] as RequestInit).body as string)
    expect(body.subscriptionId).toBe(1)
    expect(body.allocatedQuantity).toBe(50)
    expect(body.allocatedAt).toBe('2026-09-21T10:00')
    // select 미선택 시 빈 문자열로 전송됨
    expect(body.allocationMethod).toBe('')
    // number 입력 비움 시 null로 전송됨 (valueAsNumber 동작)
    expect(body.allocationPrice).toBeNull()
    // textarea 비움 시 빈 문자열로 전송됨
    expect(body.note).toBe('')
  })
})
