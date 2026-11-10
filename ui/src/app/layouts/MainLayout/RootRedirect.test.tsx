import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RootRedirect from './RootRedirect.tsx'

function renderRoot() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/dashboard" element={<div>대시보드 화면</div>} />
          <Route path="/ipo-calendar" element={<div>일정 화면</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RootRedirect', () => {
  it('홈은 로그인 여부와 관계없이 공모주 일정으로 이동', async () => {
    renderRoot()
    expect(await screen.findByText('일정 화면')).toBeInTheDocument()
    expect(screen.queryByText('대시보드 화면')).not.toBeInTheDocument()
  })
})
