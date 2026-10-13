import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCurrentInvestor } from './useCurrentInvestor.ts'

vi.mock('./useInvestors.ts', () => ({
  useInvestors: () => ({
    data: [
      { id: 1, displayName: '가' },
      { id: 2, displayName: '나' },
    ],
    isLoading: false,
  }),
}))

afterEach(() => vi.restoreAllMocks())

describe('useCurrentInvestor', () => {
  it('저장소를 못 쓰는 환경에서도 고른 투자자가 이번 방문 동안 유지된다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const { result } = renderHook(() => useCurrentInvestor())
    expect(result.current.needsSelection).toBe(true)

    act(() => result.current.select(2))

    expect(result.current.current?.id).toBe(2)
    expect(result.current.needsSelection).toBe(false)
  })
})
