import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import UnsavedGuardProvider from './UnsavedGuardProvider.tsx'
import { useRegisterUnsavedGuard, useUnsavedGuard } from './unsavedGuard.ts'

const wrapper = ({ children }: { children: ReactNode }) => (
  <UnsavedGuardProvider>{children}</UnsavedGuardProvider>
)

describe('unsavedGuard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('등록이 없으면 dirty/pending 모두 false다', () => {
    const { result } = renderHook(() => useUnsavedGuard(), { wrapper })
    expect(result.current.isDirty).toBe(false)
    expect(result.current.isPending).toBe(false)
  })

  it('dirty만 등록하면 isDirty만 true다', () => {
    const { result } = renderHook(
      () => {
        useRegisterUnsavedGuard('a', { dirty: true, pending: false })
        return useUnsavedGuard()
      },
      { wrapper },
    )
    expect(result.current.isDirty).toBe(true)
    expect(result.current.isPending).toBe(false)
  })

  it('pending만 등록하면 isPending만 true다', () => {
    const { result } = renderHook(
      () => {
        useRegisterUnsavedGuard('a', { dirty: false, pending: true })
        return useUnsavedGuard()
      },
      { wrapper },
    )
    expect(result.current.isDirty).toBe(false)
    expect(result.current.isPending).toBe(true)
  })

  it('여러 등록자 중 하나라도 dirty/pending이면 true다', () => {
    const { result } = renderHook(
      () => {
        useRegisterUnsavedGuard('a', { dirty: false, pending: false })
        useRegisterUnsavedGuard('b', { dirty: true, pending: false })
        useRegisterUnsavedGuard('c', { dirty: false, pending: true })
        return useUnsavedGuard()
      },
      { wrapper },
    )
    expect(result.current.isDirty).toBe(true)
    expect(result.current.isPending).toBe(true)
  })

  it('dirty가 false로 바뀌면 등록이 해제된다', () => {
    const { result, rerender } = renderHook(
      ({ on }) => {
        useRegisterUnsavedGuard('a', { dirty: on, pending: on })
        return useUnsavedGuard()
      },
      { wrapper, initialProps: { on: true } },
    )
    expect(result.current.isDirty).toBe(true)
    expect(result.current.isPending).toBe(true)
    rerender({ on: false })
    expect(result.current.isDirty).toBe(false)
    expect(result.current.isPending).toBe(false)
  })

  it('프로바이더가 없으면 보호가 동작하지 않고 register는 무시된다', () => {
    const { result } = renderHook(() => {
      const guard = useUnsavedGuard()
      guard.register('x', { dirty: true, pending: true })
      return guard
    })
    expect(result.current.isDirty).toBe(false)
    expect(result.current.isPending).toBe(false)
  })
})
