import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { DashboardTab } from '../tabs.ts'
import DashboardTabs from './DashboardTabs.tsx'

const mockOnTabChange = vi.fn()

function renderDashboardTabs(currentTab: DashboardTab = 'all') {
  return render(<DashboardTabs currentTab={currentTab} onTabChange={mockOnTabChange} />)
}

describe('DashboardTabs', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('모든 탭이 렌더링됨', async () => {
    renderDashboardTabs()

    expect(screen.getByRole('tab', { name: '전체' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '투자자별' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '증권사별' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '월별' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '연도별' })).toBeInTheDocument()
  })

  it('현재 탭이 aria-selected=true로 표시됨', async () => {
    renderDashboardTabs('month')

    expect(screen.getByRole('tab', { name: '월별', selected: true })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '전체', selected: false })).toBeInTheDocument()
  })

  it('aria-valid-attr-value 수정: aria-controls 속성이 없음 (critical 위반 해결)', async () => {
    renderDashboardTabs()

    const tabs = screen.getAllByRole('tab')
    tabs.forEach((tab) => {
      expect(tab).not.toHaveAttribute('aria-controls')
    })
  })

  it('각 탭에 고유한 id가 있음', async () => {
    renderDashboardTabs()

    expect(screen.getByRole('tab', { name: '전체' })).toHaveAttribute('id', 'tab-all')
    expect(screen.getByRole('tab', { name: '투자자별' })).toHaveAttribute('id', 'tab-investor')
    expect(screen.getByRole('tab', { name: '증권사별' })).toHaveAttribute(
      'id',
      'tab-securities-firm',
    )
    expect(screen.getByRole('tab', { name: '월별' })).toHaveAttribute('id', 'tab-month')
    expect(screen.getByRole('tab', { name: '연도별' })).toHaveAttribute('id', 'tab-year')
  })

  it('탭 클릭 시 onTabChange 호출됨', async () => {
    renderDashboardTabs()

    await userEvent.click(screen.getByRole('tab', { name: '월별' }))

    expect(mockOnTabChange).toHaveBeenCalledWith('month')
  })

  it('키보드 네비게이션: ArrowRight로 다음 탭 이동', async () => {
    renderDashboardTabs('all')

    const allTab = screen.getByRole('tab', { name: '전체' })
    allTab.focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(mockOnTabChange).toHaveBeenCalledWith('investor')
  })

  it('키보드 네비게이션: ArrowLeft로 이전 탭 이동', async () => {
    renderDashboardTabs('investor')

    const investorTab = screen.getByRole('tab', { name: '투자자별' })
    investorTab.focus()
    await userEvent.keyboard('{ArrowLeft}')

    expect(mockOnTabChange).toHaveBeenCalledWith('all')
  })

  it('키보드 네비게이션: Home으로 첫 탭 이동', async () => {
    renderDashboardTabs('year')

    const yearTab = screen.getByRole('tab', { name: '연도별' })
    yearTab.focus()
    await userEvent.keyboard('{Home}')

    expect(mockOnTabChange).toHaveBeenCalledWith('all')
  })

  it('키보드 네비게이션: End로 마지막 탭 이동', async () => {
    renderDashboardTabs('all')

    const allTab = screen.getByRole('tab', { name: '전체' })
    allTab.focus()
    await userEvent.keyboard('{End}')

    expect(mockOnTabChange).toHaveBeenCalledWith('year')
  })

  it('Enter로 탭 선택', async () => {
    renderDashboardTabs('all')

    const allTab = screen.getByRole('tab', { name: '전체' })
    allTab.focus()
    await userEvent.keyboard('{Enter}')

    expect(mockOnTabChange).toHaveBeenCalledWith('all')
  })

  it('Space로 탭 선택', async () => {
    renderDashboardTabs('all')

    const allTab = screen.getByRole('tab', { name: '전체' })
    allTab.focus()
    await userEvent.keyboard('[Space]')

    expect(mockOnTabChange).toHaveBeenCalledWith('all')
  })

  it('비활성 탭은 tabIndex=-1, 활성 탭은 tabIndex=0', async () => {
    renderDashboardTabs('month')

    expect(screen.getByRole('tab', { name: '월별' })).toHaveAttribute('tabIndex', '0')
    expect(screen.getByRole('tab', { name: '전체' })).toHaveAttribute('tabIndex', '-1')
  })

  it('tablist에 aria-label이 있음', async () => {
    renderDashboardTabs()

    expect(screen.getByRole('tablist')).toHaveAttribute('aria-label', '대시보드 탭')
  })
})
