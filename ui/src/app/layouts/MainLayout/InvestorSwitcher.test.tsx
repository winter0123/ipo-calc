import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestorSwitcher from './InvestorSwitcher.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import UnsavedGuardProvider from '@/shared/navigation/UnsavedGuardProvider.tsx'
import { useRegisterUnsavedGuard } from '@/shared/navigation/unsavedGuard.ts'

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

function Reg({ dirty, pending }: { dirty: boolean; pending: boolean }) {
  useRegisterUnsavedGuard('test-page', { dirty, pending })
  return null
}

function renderSwitcher({ dirty = false, pending = false } = {}) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    if (String(path) === '/investors') return [INVESTOR_1, INVESTOR_2]
    throw new Error(`unexpected path: ${String(path)}`)
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <UnsavedGuardProvider>
        <Reg dirty={dirty} pending={pending} />
        <InvestorSwitcher />
      </UnsavedGuardProvider>
    </QueryClientProvider>,
  )
}

describe('InvestorSwitcher — 전환 전 미저장/진행 중 경고', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    localStorage.setItem('currentInvestorId', '1')
  })

  it('미저장 입력이 없으면 확인 없이 바로 전환한다', async () => {
    renderSwitcher()

    await userEvent.click(await screen.findByRole('button', { name: /현재 투자자: 홍길동/ }))
    await userEvent.click(screen.getByRole('button', { name: '가족둘째' }))

    expect(localStorage.getItem('currentInvestorId')).toBe('2')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('미저장 입력이 있으면 확인을 받고, 계속 작성이면 현재 투자자를 유지한다', async () => {
    renderSwitcher({ dirty: true })

    await userEvent.click(await screen.findByRole('button', { name: /현재 투자자: 홍길동/ }))
    await userEvent.click(screen.getByRole('button', { name: '가족둘째' }))

    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('저장하지 않은 입력이 있습니다')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(localStorage.getItem('currentInvestorId')).toBe('1')
    expect(screen.getByRole('button', { name: /현재 투자자: 홍길동/ })).toBeInTheDocument()
  })

  it('미저장 입력이 있어도 바꾸기를 누르면 전환한다', async () => {
    renderSwitcher({ dirty: true })

    await userEvent.click(await screen.findByRole('button', { name: /현재 투자자: 홍길동/ }))
    await userEvent.click(screen.getByRole('button', { name: '가족둘째' }))
    await userEvent.click(await screen.findByRole('button', { name: '바꾸기' }))

    expect(localStorage.getItem('currentInvestorId')).toBe('2')
  })

  it('저장이 진행 중이면 전환할 수 없음을 알리고 이동을 막는다', async () => {
    renderSwitcher({ pending: true })

    await userEvent.click(await screen.findByRole('button', { name: /현재 투자자: 홍길동/ }))
    await userEvent.click(screen.getByRole('button', { name: '가족둘째' }))

    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('저장이 진행 중입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(localStorage.getItem('currentInvestorId')).toBe('1')
  })
})
