import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MainLayout from './MainLayout.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const investor = (id: number, displayName: string) => ({
  id,
  displayName,
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01',
  updatedAt: '2026-01-01',
})

function setup(investors: object[], path = '/investments') {
  vi.mocked(apiFetch).mockImplementation(async (p: unknown) =>
    String(p).startsWith('/investors')
      ? investors
      : { accountId: 1, email: 'a@b.com', name: '나', role: 'USER' },
  )
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="investments" element={<div>투자 내역 화면</div>} />
            <Route path="dashboard" element={<div>대시보드 화면</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('현재 투자자 선택', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
  })

  it('투자자가 1명이면 자동 선택되어 바로 화면이 열린다', async () => {
    setup([investor(1, '홍길동')])
    expect(await screen.findByText('투자 내역 화면')).toBeInTheDocument()
    expect(await screen.findByText('현재 투자자:')).toBeInTheDocument()
    expect(screen.getByRole('banner')).toHaveTextContent('홍길동')
  })

  it('2명 이상이면 직접 고를 때까지 작업 화면 대신 선택 화면이 나온다', async () => {
    setup([investor(1, '홍길동'), investor(2, '김철수')])
    expect(await screen.findByRole('heading', { name: '어느 투자자로 작업할까요?' })).toBeVisible()
    expect(screen.queryByText('투자 내역 화면')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '김철수' }))

    expect(await screen.findByText('투자 내역 화면')).toBeInTheDocument()
    expect(screen.getByLabelText('현재 투자자')).toHaveValue('2')
  })

  it('전환기로 투자자를 바꿀 수 있고 선택은 기억된다', async () => {
    localStorage.setItem('currentInvestorId', '1')
    setup([investor(1, '홍길동'), investor(2, '김철수')])
    const select = await screen.findByLabelText('현재 투자자')
    expect(select).toHaveValue('1')
    await userEvent.selectOptions(select, '2')
    expect(localStorage.getItem('currentInvestorId')).toBe('2')
  })

  it('저장된 선택이 목록에 없으면 다시 고르게 한다', async () => {
    localStorage.setItem('currentInvestorId', '99')
    setup([investor(1, '홍길동'), investor(2, '김철수')])
    expect(await screen.findByRole('heading', { name: '어느 투자자로 작업할까요?' })).toBeVisible()
  })

  it('계정 단위 화면(대시보드)은 선택 없이도 열린다', async () => {
    setup([investor(1, '홍길동'), investor(2, '김철수')], '/dashboard')
    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument()
  })
})
