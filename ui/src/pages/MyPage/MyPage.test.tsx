import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MyPage from './MyPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

describe('MyPage', () => {
  it('가입 시 입력한 계정과 본인 투자자 정보를 보여주고 비밀번호는 보여주지 않는다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      if (path === '/auth/me') {
        return { accountId: 1, email: 'joy@example.com', name: '조이', role: 'USER' }
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
        <MyPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('joy@example.com')).toBeInTheDocument()
    expect(screen.getByText('조이')).toBeInTheDocument()
    expect(screen.getByText('기쁨투자자')).toBeInTheDocument()
    expect(screen.getByText('본인')).toBeInTheDocument()
    expect(screen.queryByText(/비밀번호/)).toHaveTextContent(
      '비밀번호는 보안을 위해 표시하지 않습니다.',
    )
  })
})
