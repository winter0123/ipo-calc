import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import NotFoundPage from './NotFoundPage.tsx'

describe('NotFoundPage', () => {
  it('안내 문구와 대시보드 링크를 보여준다', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대시보드로 이동' })).toHaveAttribute(
      'href',
      '/dashboard',
    )
  })
})
