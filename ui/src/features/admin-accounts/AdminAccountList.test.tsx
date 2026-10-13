import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import AdminAccountList from './AdminAccountList.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const account = (over: Record<string, unknown>) => ({
  id: 1,
  email: 'wait@example.com',
  name: '대기자',
  role: 'USER',
  approved: false,
  createdAt: '2026-10-01T09:30:00',
  locked: false,
  ...over,
})

const writes = () => vi.mocked(apiFetch).mock.calls.filter(([, init]) => init?.method === 'POST')

function setup(rows: unknown[] | Error) {
  vi.mocked(apiFetch).mockImplementation(async (_path: unknown, init?: RequestInit) => {
    if (init?.method === 'POST') return undefined
    if (rows instanceof Error) throw rows
    return rows
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AdminAccountList />
    </QueryClientProvider>,
  )
}

describe('AdminAccountList', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('기본은 승인 대기만 조회하고 계정을 보여준다', async () => {
    setup([account({})])

    expect(await screen.findByText('wait@example.com')).toBeInTheDocument()
    expect(screen.getByText('2026-10-01 09:30')).toBeInTheDocument()
    expect(apiFetch).toHaveBeenCalledWith('/admin/accounts?approved=false', expect.anything())
  })

  it('승인 버튼을 누르면 승인 요청을 보낸다', async () => {
    setup([account({ id: 7 })])
    await userEvent.click(await screen.findByRole('button', { name: 'wait@example.com 승인' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/admin/accounts/7/approve', { method: 'POST' }),
    )
  })

  it('승인된 계정은 승인 취소 버튼을 보여주고 취소 요청을 보낸다', async () => {
    setup([account({ id: 8, email: 'ok@example.com', approved: true })])
    await userEvent.click(screen.getByLabelText('승인 대기만 보기'))
    await userEvent.click(await screen.findByRole('button', { name: 'ok@example.com 승인 취소' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/admin/accounts/8/revoke', { method: 'POST' }),
    )
  })

  it('전체 보기로 바꾸면 쿼리 없이 조회한다', async () => {
    setup([])
    await userEvent.click(screen.getByLabelText('승인 대기만 보기'))

    await waitFor(() => expect(apiFetch).toHaveBeenCalledWith('/admin/accounts', expect.anything()))
    expect(await screen.findByText('계정이 없습니다.')).toBeInTheDocument()
  })

  it('대기 계정이 없으면 빈 상태를 보여준다', async () => {
    setup([])

    expect(await screen.findByText('승인 대기 중인 계정이 없습니다.')).toBeInTheDocument()
  })

  it('관리자가 아니면(403) 안내만 보여주고 요청하지 않는다', async () => {
    setup(new ApiError(403, 'Forbidden'))

    expect(await screen.findByRole('alert')).toHaveTextContent('관리자만 볼 수 있는 화면입니다.')
    expect(writes()).toHaveLength(0)
  })

  it('잠긴 계정에만 잠금 해제 버튼이 보이고 누르면 해제 요청을 보낸다', async () => {
    setup([
      account({ id: 9, email: 'locked@example.com', approved: true, locked: true }),
      account({ id: 10, email: 'free@example.com', approved: true }),
    ])
    await userEvent.click(
      await screen.findByRole('button', { name: 'locked@example.com 잠금 해제' }),
    )

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/admin/accounts/9/unlock', { method: 'POST' }),
    )
    expect(screen.queryByRole('button', { name: 'free@example.com 잠금 해제' })).toBeNull()
  })
})
