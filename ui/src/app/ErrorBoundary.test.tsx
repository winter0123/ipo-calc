import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import ErrorBoundary from './ErrorBoundary.tsx'

function Boom(): never {
  throw new Error('boom')
}

describe('ErrorBoundary', () => {
  afterEach(() => vi.restoreAllMocks())

  it('정상 렌더 시 자식을 그대로 보여준다', () => {
    render(
      <ErrorBoundary>
        <p>ok</p>
      </ErrorBoundary>,
    )
    expect(screen.getByText('ok')).toBeInTheDocument()
  })

  it('렌더 오류 시 안내와 새로고침 버튼·대시보드 링크를 role=alert로 보여준다', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('문제가 발생했습니다')
    expect(screen.getByRole('button', { name: '새로고침' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대시보드로 이동' })).toHaveAttribute(
      'href',
      '/dashboard',
    )
  })
})
