import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LandingPage from './LandingPage.tsx'

function renderPage(loggedIn: boolean) {
  return render(
    <MemoryRouter>
      <LandingPage loggedIn={loggedIn} />
    </MemoryRouter>,
  )
}

describe('LandingPage', () => {
  it('비로그인: 공모주 일정 보기·무료로 시작하기 링크', () => {
    renderPage(false)
    for (const link of screen.getAllByRole('link', { name: '무료로 시작하기' })) {
      expect(link).toHaveAttribute('href', '/signup')
    }
    for (const link of screen.getAllByRole('link', { name: '공모주 일정 보기' })) {
      expect(link).toHaveAttribute('href', '/ipo-calendar')
    }
    expect(screen.queryByRole('link', { name: '대시보드로' })).not.toBeInTheDocument()
  })

  it('로그인: 무료로 시작하기 대신 대시보드로', () => {
    renderPage(true)
    for (const link of screen.getAllByRole('link', { name: '대시보드로' })) {
      expect(link).toHaveAttribute('href', '/dashboard')
    }
    expect(screen.queryByRole('link', { name: '무료로 시작하기' })).not.toBeInTheDocument()
  })

  it('h1 하나, 기능 소개 4개, 대체 텍스트가 있는 캡처 이미지', () => {
    renderPage(false)
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(
      screen.getByRole('heading', { level: 2, name: '매도 내역은 증권사에서 가져옵니다' }),
    ).toBeInTheDocument()
    const images = screen.getAllByRole('img')
    expect(images).toHaveLength(5)
    images.forEach((img) => expect(img).toHaveAttribute('alt', expect.stringMatching(/\S/)))
  })
})
