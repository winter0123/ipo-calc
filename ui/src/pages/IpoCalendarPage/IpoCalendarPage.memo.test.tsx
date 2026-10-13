import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import IpoCalendarPage from './IpoCalendarPage.tsx'

const seen: unknown[] = []

vi.mock('@/entities/ipo/index.ts', () => ({
  // 렌더마다 새 배열을 돌려줘도 visibleTypes 참조는 searchParams가 같으면 그대로여야 한다
  useIpos: () => ({ data: [{ id: 1 }], isLoading: false, error: null }),
}))
vi.mock('@/widgets/IpoCalendar/index.ts', () => ({
  IpoCalendar: ({ visibleTypes }: { visibleTypes: unknown }) => {
    seen.push(visibleTypes)
    return null
  },
}))

describe('IpoCalendarPage visibleTypes', () => {
  it('다시 렌더돼도 같은 Set 참조를 넘긴다', () => {
    const ui = () => (
      <MemoryRouter>
        <IpoCalendarPage />
      </MemoryRouter>
    )
    const { rerender } = render(ui())
    rerender(ui())
    expect(seen.length).toBeGreaterThanOrEqual(2)
    expect(new Set(seen).size).toBe(1)
  })
})
