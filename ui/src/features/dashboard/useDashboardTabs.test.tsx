import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { QueryClient } from '@tanstack/react-query'
import { dashboardKeys } from '@/entities/dashboard/queryKeys.ts'
import { useDashboardTabs } from './useDashboardTabs.ts'

function seed(queryClient: QueryClient) {
  queryClient.setQueryData(dashboardKeys.summary(), {})
  queryClient.setQueryData(dashboardKeys.byPeriod('month', 2026, 9), [])
  queryClient.setQueryData(dashboardKeys.byPeriod('year', 2026), [])
}

const isInvalidated = (queryClient: QueryClient, key: readonly unknown[]) =>
  queryClient.getQueryState(key)?.isInvalidated

describe('useDashboardTabs.invalidateCurrentTab', () => {
  it('월별 탭에서는 월별 기간 쿼리만 무효화한다', () => {
    const queryClient = new QueryClient()
    seed(queryClient)
    const { result } = renderHook(() => useDashboardTabs())

    act(() => result.current.setCurrentTab('month'))
    act(() => result.current.invalidateCurrentTab(queryClient))

    expect(isInvalidated(queryClient, dashboardKeys.byPeriod('month', 2026, 9))).toBe(true)
    expect(isInvalidated(queryClient, dashboardKeys.byPeriod('year', 2026))).toBe(false)
    expect(isInvalidated(queryClient, dashboardKeys.summary())).toBe(false)
  })

  it('연도별 탭에서는 연도별 기간 쿼리만 무효화한다', () => {
    const queryClient = new QueryClient()
    seed(queryClient)
    const { result } = renderHook(() => useDashboardTabs())

    act(() => result.current.setCurrentTab('year'))
    act(() => result.current.invalidateCurrentTab(queryClient))

    expect(isInvalidated(queryClient, dashboardKeys.byPeriod('year', 2026))).toBe(true)
    expect(isInvalidated(queryClient, dashboardKeys.byPeriod('month', 2026, 9))).toBe(false)
  })

  it('전체 탭에서는 summary 쿼리를 무효화한다', () => {
    const queryClient = new QueryClient()
    seed(queryClient)
    const { result } = renderHook(() => useDashboardTabs())

    act(() => result.current.invalidateCurrentTab(queryClient))

    expect(isInvalidated(queryClient, dashboardKeys.summary())).toBe(true)
  })
})
