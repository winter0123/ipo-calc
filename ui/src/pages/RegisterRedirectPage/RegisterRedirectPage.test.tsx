import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RegisterRedirectPage from './RegisterRedirectPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const INVESTOR_1 = {
  id: 1,
  displayName: '홍길동',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const INVESTOR_2 = {
  id: 2,
  displayName: '가족둘째',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const SUBSCRIPTION = {
  id: 55,
  investorId: 1,
  securitiesAccountId: 10,
  ipoId: 7,
  quantity: 10,
  depositAmount: 125000,
  subscribedAt: '2026-11-20T09:00:00Z',
  memo: null,
  status: 'ACTIVE',
  createdAt: '2026-11-20T00:00:00Z',
  updatedAt: '2026-11-20T00:00:00Z',
}

function LocationProbe() {
  const location = useLocation()
  return (
    <div>
      <h1>청약·매도 관리</h1>
      <output data-testid="from">{(location.state as { from?: string } | null)?.from ?? ''}</output>
      <output data-testid="search">{location.search}</output>
    </div>
  )
}

function renderAt(url: string) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const p = String(path)
    if (p === '/investors') return [INVESTOR_1, INVESTOR_2]
    if (p === '/subscriptions/55') return SUBSCRIPTION
    if (p === '/subscriptions/99') throw new ApiError(404, '청약을 찾을 수 없습니다')
    if (p === '/subscriptions/77') throw new ApiError(403, '다른 계정의 청약입니다')
    throw new Error(`unexpected path: ${p}`)
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/sales/register" element={<RegisterRedirectPage />} />
          <Route path="/subscriptions/register" element={<RegisterRedirectPage />} />
          <Route path="/allocations/register" element={<RegisterRedirectPage />} />
          <Route path="/ipos/:ipoId/manage" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('예전 등록 링크 호환 리다이렉트', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    localStorage.setItem('currentInvestorId', '1')
  })

  it('문맥이 없으면 공모주 목록 안내를 보여 준다', async () => {
    renderAt('/sales/register')

    expect(
      await screen.findByRole('heading', { name: '매도·청약 등록은 공모주를 고른 뒤 시작합니다' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '공모주 청약 목록으로 이동' })).toHaveAttribute(
      'href',
      '/subscriptions',
    )
  })

  it('ipoId가 있으면 통합 관리 화면으로 replace 이동한다', async () => {
    renderAt('/sales/register?ipoId=7')

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
  })

  it('잘못된 ipoId면 안내를 보여 준다', async () => {
    renderAt('/sales/register?ipoId=abc')

    expect(await screen.findByRole('alert')).toHaveTextContent('링크의 주소가 올바르지 않습니다')
  })

  it('subscriptionId로 권한 확인된 상세를 매핑해 통합 관리 화면으로 이동한다', async () => {
    renderAt('/allocations/register?subscriptionId=55')

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
  })

  it('기록의 투자자가 다른 경우 글로벌 투자자를 임의로 바꾸지 않고 전환 안내만 보여 준다', async () => {
    // 현재 투자자는 홍길동(1), 기록은 홍길동의 기록(investorId 1) — 같은 투자자면 바로 이동한다
    renderAt('/sales/register?subscriptionId=55')

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
  })

  it('현재 투자자가 기록의 투자자와 다르면 안내를 보여 주고 자동 전환하지 않는다', async () => {
    // SUBSCRIPTION은 investorId 1 소유 — 현재 투자자를 2로 바꾸면 불일치 상황이 된다
    localStorage.setItem('currentInvestorId', '2')
    renderAt('/sales/register?subscriptionId=55')

    expect(await screen.findByText(/홍길동님의 청약·배정·매도 기록입니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '홍길동님으로 전환하고 열기' })).toBeInTheDocument()
    // 임의로 전환하지 않았다 — 여전히 가족둘째(2)다
    expect(localStorage.getItem('currentInvestorId')).toBe('2')
  })

  it('전환 버튼을 누른 경우에만 투자자를 바꿔 통합 관리 화면으로 이동한다', async () => {
    localStorage.setItem('currentInvestorId', '2')
    renderAt('/sales/register?subscriptionId=55')

    await userEvent.click(await screen.findByRole('button', { name: '홍길동님으로 전환하고 열기' }))

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
    expect(localStorage.getItem('currentInvestorId')).toBe('1')
  })

  it('subscriptionId가 없는 기록이면 403/404 안내를 보여 준다', async () => {
    renderAt('/sales/register?subscriptionId=99')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다',
    )
  })

  it('subscriptionId가 다른 계정의 기록이면 403 안내를 보여 준다', async () => {
    renderAt('/sales/register?subscriptionId=77')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다',
    )
  })

  it('ipoId와 subscriptionId가 서로 다르면 안내를 보여 준다', async () => {
    renderAt('/sales/register?ipoId=8&subscriptionId=55')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크의 공모주와 청약 정보가 서로 다릅니다',
    )
  })

  it('ipoId 리다이렉트에서 state.from을 보존한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const p = String(path)
      if (p === '/investors') return [INVESTOR_1]
      throw new Error(`unexpected path: ${p}`)
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter
          initialEntries={[
            { pathname: '/sales/register', search: '?ipoId=7', state: { from: '/ipos/7' } },
          ]}
        >
          <Routes>
            <Route path="/sales/register" element={<RegisterRedirectPage />} />
            <Route path="/ipos/:ipoId/manage" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
    expect(screen.getByTestId('from')).toHaveTextContent('/ipos/7')
  })
  it('subscriptionId 리다이렉트에서 원래 계좌 문맥을 URL에 보존한다', async () => {
    renderAt('/sales/register?subscriptionId=55')

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
    expect(screen.getByTestId('search')).toHaveTextContent('securitiesAccountId=10')
  })
})
