import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SecuritiesPage from './SecuritiesPage.tsx'

vi.mock('@/entities/investor/index.ts', () => ({
  useCurrentInvestor: () => ({
    current: { id: 1, displayName: '기쁨투자자' },
    isLoading: false,
  }),
}))

vi.mock('@/features/securities-manage/index.ts', () => ({
  SecuritiesManage: () => <div>증권사 계좌 목록</div>,
}))

describe('SecuritiesPage', () => {
  it('증권사 계좌 관리 화면 하단에서 연동 화면으로 이동할 수 있다', () => {
    render(
      <MemoryRouter>
        <SecuritiesPage />
      </MemoryRouter>,
    )

    expect(screen.getByText('증권사 계좌 목록')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '증권사 연동' })).toHaveAttribute(
      'href',
      '/broker-sync',
    )
  })
})
