import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import AdminUserList from '@/features/admin-users/AdminUserList.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const user = (over: Record<string, unknown>) => ({
  id: 1,
  email: 'a@b.com',
  name: '이름',
  nickname: '닉네임',
  role: 'USER',
  approved: true,
  locked: false,
  lockedUntil: null,
  lastLoginAt: null,
  createdAt: '2026-10-01T09:30:00',
  investorCount: 2,
  subscriptionCount: 3,
  ...over,
})

const page = (content: unknown[], totalPages = 1, totalElements = content.length) => ({
  content,
  totalPages,
  totalElements,
  number: 0,
  size: 20,
})

function setup(rows: unknown[] | Error, totalPages = 1) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    if (rows instanceof Error) throw rows
    if (String(path).startsWith('/admin/users')) return page(contentOf(rows), totalPages)
    return {}
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/users']}>
        <AdminUserList />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function contentOf(rows: unknown[] | Error) {
  return rows instanceof Error ? [] : rows
}

describe('AdminUserList', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
    localStorage.clear()
  })

  it('기본 페이지(0, size 20)를 조회해 회원을 보여준다', async () => {
    setup([user({})])
    expect(await screen.findByText('a@b.com')).toBeInTheDocument()
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringMatching(/^\/admin\/users\?page=0&size=20/),
        expect.anything(),
      ),
    )
  })

  it('검색어 입력 후 검색 버튼을 누르면 검색 파라미터를 보낸다', async () => {
    setup([user({})])
    await screen.findByText('a@b.com')
    await userEvent.type(screen.getByPlaceholderText('이메일·닉네임·이름'), 'a@b')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))
    expect(vi.mocked(apiFetch)).toHaveBeenCalledTimes(2)
    const lastPath = String(vi.mocked(apiFetch).mock.calls[1][0])
    const searchParam = new URL(lastPath, 'http://x').searchParams.get('search')
    expect(searchParam).toBe('a@b')
  })

  it('다음 페이지 버튼을 누르면 page를 증가시켜 조회한다', async () => {
    setup([user({ id: 1 }), user({ id: 2, email: 'c@d.com' })], 2)
    await screen.findByText('a@b.com')
    await userEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(vi.mocked(apiFetch)).toHaveBeenCalledTimes(2)
    const lastPath = String(vi.mocked(apiFetch).mock.calls[1][0])
    const params = new URL(lastPath, 'http://x').searchParams
    expect(params.get('page')).toBe('1')
  })

  it('검색 조건을 초기화하면 다시 기본 조회를 보낸다', async () => {
    setup([user({})])
    await screen.findByText('a@b.com')
    await userEvent.type(screen.getByPlaceholderText('이메일·닉네임·이름'), 'x')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))
    expect(vi.mocked(apiFetch)).toHaveBeenCalledTimes(2)
    await userEvent.click(screen.getByRole('button', { name: '초기화' }))
    expect(vi.mocked(apiFetch)).toHaveBeenCalledTimes(3)
    const lastPath = String(vi.mocked(apiFetch).mock.calls[2][0])
    const params = new URL(lastPath, 'http://x').searchParams
    expect(params.get('page')).toBe('0')
    expect(params.get('size')).toBe('20')
    expect(params.get('search')).toBeNull()
  })

  it('조회 결과가 없으면 빈 상태를 보여준다', async () => {
    setup([])
    expect(await screen.findByText('조회된 회원이 없습니다.')).toBeInTheDocument()
  })

  it('403 응답이면 관리자 전용 안내만 보여주고 변경을 보내지 않는다', async () => {
    setup(new ApiError(403, 'Forbidden'))
    expect(await screen.findByRole('alert')).toHaveTextContent('관리자만 볼 수 있는 화면입니다.')
    const posts = vi.mocked(apiFetch).mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts).toHaveLength(0)
  })
})
