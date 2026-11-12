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

function setup(
  investors: object[],
  path = '/investments',
  accounts: object[] = [
    {
      id: 10,
      investorId: 1,
      securitiesFirmId: 1,
      securitiesFirmName: '증권사',
      accountNumber: '1234',
      accountName: null,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    },
  ],
) {
  vi.mocked(apiFetch).mockImplementation(async (p: unknown) =>
    String(p) === '/investors'
      ? investors
      : String(p).startsWith('/securities-accounts')
        ? accounts
        : { accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'USER' },
  )
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="investments" element={<div>투자 내역 화면</div>} />
            <Route path="subscriptions" element={<div>청약 화면</div>} />
            <Route path="dashboard" element={<div>대시보드 화면</div>} />
            <Route path="securities" element={<div>증권사 관리 화면</div>} />
            <Route path="my-page" element={<div>마이페이지 화면</div>} />
            <Route path="ipos/:ipoId" element={<div>공개 상세 화면</div>} />
            <Route path="ipos/:ipoId/manage" element={<div>청약·매도 관리 화면</div>} />
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

  it('현재 투자자의 증권계좌가 없으면 등록 안내 팝업을 띄운다', async () => {
    setup([investor(1, '홍길동')], '/subscriptions', [])

    expect(
      await screen.findByRole('alertdialog', { name: '증권계좌를 먼저 등록해 주세요' }),
    ).toBeVisible()
    expect(screen.queryByText('청약 화면')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '마이페이지' }))
    expect(await screen.findByText('마이페이지 화면')).toBeInTheDocument()
  })

  it('투자자가 0명이면 작업 화면 대신 투자자 등록 안내가 나온다', async () => {
    setup([], '/subscriptions')
    expect(
      await screen.findByRole('heading', { name: '먼저 투자자를 등록해 주세요' }),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: '투자자 등록하기' })).toHaveAttribute(
      'href',
      '/investors/register',
    )
    expect(screen.queryByText('청약 화면')).not.toBeInTheDocument()
  })

  it('2명 이상이면 직접 고를 때까지 작업 화면 대신 선택 화면이 나온다', async () => {
    setup([investor(1, '홍길동'), investor(2, '김철수')])
    expect(await screen.findByRole('heading', { name: '어느 투자자로 작업할까요?' })).toBeVisible()
    expect(screen.queryByText('투자 내역 화면')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '김철수' }))

    expect(await screen.findByText('투자 내역 화면')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /현재 투자자: 김철수/ })).toBeInTheDocument()
  })

  it('전환기로 투자자를 바꿀 수 있고 선택은 기억된다', async () => {
    localStorage.setItem('currentInvestorId', '1')
    setup([investor(1, '홍길동'), investor(2, '김철수')])
    const switcher = await screen.findByRole('button', { name: /현재 투자자: 홍길동/ })
    await userEvent.click(switcher)
    await userEvent.click(screen.getByRole('button', { name: '김철수' }))
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

  it('공개 공모주 상세는 선택 없이도 열린다(관리 경로와 달리 게이트 대상이 아니다)', async () => {
    setup([investor(1, '홍길동'), investor(2, '김철수')], '/ipos/7')
    expect(await screen.findByText('공개 상세 화면')).toBeInTheDocument()
  })

  it('관리 경로(/ipos/:ipoId/manage)는 투자자를 골라야 열린다', async () => {
    // 이전 테스트에서 모듈 메모리에 남은 선택을 무효화하려고 목록에 없는 id를 심는다.
    localStorage.setItem('currentInvestorId', '99')
    setup([investor(1, '홍길동'), investor(2, '김철수')], '/ipos/7/manage')
    expect(await screen.findByRole('heading', { name: '어느 투자자로 작업할까요?' })).toBeVisible()
    expect(screen.queryByText('청약·매도 관리 화면')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '김철수' }))
    expect(await screen.findByText('청약·매도 관리 화면')).toBeInTheDocument()
  })

  it('관리 경로도 관리 계좌가 없으면 등록 안내 팝업을 띄운다', async () => {
    setup([investor(1, '홍길동')], '/ipos/7/manage', [])

    expect(
      await screen.findByRole('alertdialog', { name: '증권계좌를 먼저 등록해 주세요' }),
    ).toBeVisible()
    expect(screen.queryByText('청약·매도 관리 화면')).not.toBeInTheDocument()
  })
})
