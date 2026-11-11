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
  it('증권사 관리 화면에 계좌 목록을 보여 주고 증권사 연동 CTA는 노출하지 않는다', () => {
    render(
      <MemoryRouter>
        <SecuritiesPage />
      </MemoryRouter>,
    )

    expect(screen.getByText('증권사 계좌 목록')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '증권사 연동' })).not.toBeInTheDocument()
  })
})
