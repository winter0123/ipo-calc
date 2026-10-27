import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AllocationRegisterForm from './AllocationRegisterForm.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

vi.mock('@/entities/investor/index.ts', () => ({
  useCurrentInvestor: () => ({ current: { id: 1 }, isLoading: false }),
}))

vi.mock('@/entities/ipo/index.ts', () => ({
  useIpo: () => ({ data: undefined, isLoading: false }),
}))

vi.mock('@/entities/securities-account/index.ts', () => ({
  useSecuritiesAccounts: () => ({ data: [], isLoading: false }),
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
  investorName: null,
  securitiesFirmName: null,
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
    expect(screen.getByText(/청약수량 100주/)).toBeInTheDocument()
  })
}

async function fillValidForm() {
  await fill(screen.getByLabelText('배정수량'), '50')
  await fill(screen.getByLabelText('배정일'), '2026-09-21')
  await fill(screen.getByLabelText('배정방식 (선택)'), 'PROPORTIONAL')
  await fill(screen.getByLabelText('배정단가 (선택)'), '39000')
  await fill(screen.getByLabelText('비고 (선택)'), '테스트 배정')
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

    // 청약 정보는 입력칸이 아니라 글로만 보인다(내부 ID 없이 공모주 이름·청약일·수량)
    expect(screen.queryByDisplayValue('100')).not.toBeInTheDocument()
    expect(screen.getByText(/청약일 2026\. 9\. 1\./)).toBeInTheDocument()
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
          deletedAt: null,
          createdAt: '2026-09-21T00:00:00Z',
          updatedAt: '2026-09-21T00:00:00Z',
        }
      }
      throw new Error(`unexpected path: ${String(path)}`)
    })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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
    expect(body.allocatedAt).toBe('2026-09-21T09:00')
    expect(body.allocationMethod).toBe('PROPORTIONAL')
    expect(body.allocationPrice).toBe(39000)
    expect(body.note).toBe('테스트 배정')

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })

  it('필수값(배정수량, 배정일) 미입력 시 클라이언트 사이드 검증 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    await userEvent.clear(screen.getByLabelText('배정일'))
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('배정수량을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('배정일을 선택해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/allocations',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('배정수량이 음수이면 에러 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    await fillValidForm()
    await fill(screen.getByLabelText('배정수량'), '-1')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('0 이상 정수를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/allocations',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('배정 결과가 0주인 입력을 허용해 서버에 전송한다', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      if (path === '/investment-history/1' && (!options || options.method === 'GET'))
        return SUBSCRIPTION_DETAIL
      if (path === '/allocations' && options?.method === 'POST') return { id: 4 }
      throw new Error(`unexpected path: ${String(path)}`)
    })
    await fillValidForm()
    await fill(screen.getByLabelText('배정수량'), '0')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        '/allocations',
        expect.objectContaining({ method: 'POST' }),
      ),
    )
    const call = vi
      .mocked(apiFetch)
      .mock.calls.find(([path, opts]) => path === '/allocations' && opts?.method === 'POST')!
    expect(JSON.parse((call[1] as RequestInit).body as string).allocatedQuantity).toBe(0)
  })

  it('배정단가가 음수이면 에러 메시지 표시', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()

    await fillValidForm()
    await fill(screen.getByLabelText('배정단가 (선택)'), '-1')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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
    await fill(screen.getByLabelText('배정수량'), '50')
    await fill(screen.getByLabelText('배정일'), '2026-09-21')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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
    expect(body.allocatedAt).toBe('2026-09-21T09:00')
    // select 미선택 시 빈 문자열로 전송됨
    expect(body.allocationMethod).toBe('')
    // number 입력 비움 시 null로 전송됨 (valueAsNumber 동작)
    expect(body.allocationPrice).toBeNull()
    // textarea 비움 시 빈 문자열로 전송됨
    expect(body.note).toBe('')
  })

  it('배정일은 오늘로 채워져 있고 배정단가는 천 단위 쉼표로 보인다', async () => {
    mockSubscriptionDetailFetch()
    renderForm()
    await waitForDetailLoaded()
    expect(screen.getByLabelText('배정일')).toHaveValue(new Date().toLocaleDateString('sv-SE'))
    await fill(screen.getByLabelText('배정단가 (선택)'), '39000')
    expect(screen.getByLabelText('배정단가 (선택)')).toHaveDisplayValue('39,000')
    expect(screen.getByLabelText('배정단가 (선택)')).toHaveAttribute('inputmode', 'numeric')
  })
})
