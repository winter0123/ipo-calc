import { createContext, useContext, useEffect } from 'react'

/**
 * 전역 미저장/진행 중 보호 가드의 상태 저장소(컴포넌트 아님).
 *
 * - 페이지는 `useRegisterUnsavedGuard(id, { dirty, pending })`로 자기 상태를 신고한다.
 *   - dirty: 저장하지 않은 입력이 있다 — 이동하려면 확인을 받는다.
 *   - pending: 저장·필수 재조회가 진행 중이다 — 이동을 막는다(결과 확인 불가).
 * - 어디로 이동하는지는 가드가 판단하지 않는다. 호출부가 알아서 확인을 보여 준다.
 * - 프로바이더가 없으면(단위 테스트 등) 보호는 동작하지 않는다.
 */

export interface UnsavedGuardRegistration {
  dirty: boolean
  pending: boolean
}

export interface UnsavedGuardContextValue {
  isDirty: boolean
  isPending: boolean
  register: (id: string, state: UnsavedGuardRegistration | null) => void
}

const NOOP: UnsavedGuardContextValue = {
  isDirty: false,
  isPending: false,
  register: () => {},
}

export const UnsavedGuardContext = createContext<UnsavedGuardContextValue>(NOOP)

/** 가드 상태를 읽는 쪽(이동 차단 대화상자, 투자자 전환기 등). 프로바이더가 없으면 보호 없음. */
export function useUnsavedGuard(): UnsavedGuardContextValue {
  return useContext(UnsavedGuardContext)
}

/** 페이지는 이 훅으로 자기 상태를 등록한다. 언마운트되면 자동 해제된다. */
export function useRegisterUnsavedGuard(id: string, state: UnsavedGuardRegistration) {
  const { register } = useContext(UnsavedGuardContext)
  const { dirty, pending } = state
  useEffect(() => {
    if (!dirty && !pending) return
    register(id, { dirty, pending })
    return () => register(id, null)
  }, [id, dirty, pending, register])
}
