import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MyPage from './MyPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

describe('MyPage', () => {
  it('가입 시 입력한 계정과 본인 투자자 정보를 보여주고 비밀번호는 보여주지 않는다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      if (path === '/auth/me') {
        return {
          accountId: 1,
          email: 'joy@example.com',
          name: '조이',
          nickname: '조이닉',
          role: 'USER',
        }
      }
      if (path === '/investors') {
        return [
          {
            id: 1,
            displayName: '기쁨투자자',
            relationship: 'SELF',
            accountId: 1,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-22T00:00:00',
            updatedAt: '2026-10-22T00:00:00',
          },
          {
            id: 2,
            displayName: '가족',
            relationship: 'SPOUSE',
            accountId: 1,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-22T00:00:00',
            updatedAt: '2026-10-22T00:00:00',
          },
        ]
      }
      throw new Error(`Unexpected API path: ${String(path)}`)
    })

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter>
          <MyPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('joy@example.com')).toBeInTheDocument()
    expect(screen.getByText('조이')).toBeInTheDocument()
    expect(screen.getByText('조이닉')).toBeInTheDocument()
    expect(screen.getAllByText('기쁨투자자')).toHaveLength(2)
    expect(screen.getAllByText('본인')).toHaveLength(2)
    expect(screen.getByText('가족')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '사용자 현황' })).toBeInTheDocument()
    expect(screen.getByText('현재 등록된 투자자 2명')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '투자자 추가' })).toHaveAttribute(
      'href',
      '/investors/register',
    )
    expect(screen.getByRole('link', { name: '증권사 계좌 등록' })).toHaveAttribute(
      'href',
      '/securities',
    )
    expect(screen.queryByText(/비밀번호/)).toHaveTextContent(
      '비밀번호는 보안을 위해 표시하지 않습니다.',
    )
  })
})
