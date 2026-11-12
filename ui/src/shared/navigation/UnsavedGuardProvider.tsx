import { useCallback, useMemo, useState, type ReactNode } from 'react'
import {
  UnsavedGuardContext,
  type UnsavedGuardRegistration,
  type UnsavedGuardContextValue,
} from './unsavedGuard.ts'

/** 전역 미저장/진행 중 가드의 프로바이더. AppProviders의 라우터 안쪽에 마운트한다. */
export default function UnsavedGuardProvider({ children }: { children: ReactNode }) {
  const [entries, setEntries] = useState<Record<string, UnsavedGuardRegistration>>({})

  const register = useCallback((id: string, state: UnsavedGuardRegistration | null) => {
    setEntries((previous) => {
      if (state === null) {
        if (!(id in previous)) return previous
        const next = { ...previous }
        delete next[id]
        return next
      }
      const current = previous[id]
      if (current && current.dirty === state.dirty && current.pending === state.pending) {
        return previous
      }
      return { ...previous, [id]: state }
    })
  }, [])

  const value = useMemo<UnsavedGuardContextValue>(() => {
    const all = Object.values(entries)
    return {
      isDirty: all.some((entry) => entry.dirty),
      isPending: all.some((entry) => entry.pending),
      register,
    }
  }, [entries, register])

  return <UnsavedGuardContext.Provider value={value}>{children}</UnsavedGuardContext.Provider>
}
