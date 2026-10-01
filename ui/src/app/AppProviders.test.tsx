import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import AppProviders from './AppProviders.tsx'
import { ApiError } from '@/shared/api/apiError.ts'

function Probe() {
  const location = useLocation()
  const { error } = useQuery({
    queryKey: ['probe'],
    queryFn: () => Promise.reject(new ApiError(401, 'Unauthorized')),
  })
  return (
    <div>
      <p data-testid="path">{location.pathname}</p>
      <p data-testid="from">{(location.state as { from?: string } | null)?.from ?? ''}</p>
      <p>{error ? 'failed' : 'loading'}</p>
    </div>
  )
}

describe('AppProviders 401 처리', () => {
  beforeEach(() => vi.spyOn(console, 'error').mockImplementation(() => undefined))

  it('보호 경로의 401은 /login으로 보내고 복귀 경로를 남긴다', async () => {
    window.history.pushState({}, '', '/investments?x=1')
    render(
      <AppProviders>
        <Probe />
      </AppProviders>,
    )
    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/login'))
    expect(screen.getByTestId('from')).toHaveTextContent('/investments?x=1')
  })

  it('/login에서는 redirect하지 않는다', async () => {
    window.history.pushState({}, '', '/login')
    render(
      <AppProviders>
        <Probe />
      </AppProviders>,
    )
    await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument())
    expect(screen.getByTestId('path')).toHaveTextContent('/login')
    expect(screen.getByTestId('from')).toBeEmptyDOMElement()
  })
})
