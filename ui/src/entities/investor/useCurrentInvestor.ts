import { useCallback, useSyncExternalStore } from 'react'
import { useInvestors } from './useInvestors.ts'
import type { InvestorResponse } from './types.ts'

const STORAGE_KEY = 'currentInvestorId'
const CHANGE_EVENT = 'current-investor-change'

// 선택은 브라우저에 기억한다. 저장소를 못 쓰는 환경에서도 화면은 동작해야 하므로 예외는 삼킨다.
function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGE_EVENT, onChange)
  window.addEventListener('storage', onChange)
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange)
    window.removeEventListener('storage', onChange)
  }
}

export interface CurrentInvestor {
  investors: InvestorResponse[]
  /** 지금 작업 중인 투자자. 투자자가 0명이거나 2명 이상인데 아직 고르지 않았으면 null. */
  current: InvestorResponse | null
  /** 투자자가 2명 이상인데 아직 고르지 않아 선택이 필요한 상태. */
  needsSelection: boolean
  isLoading: boolean
  select: (investorId: number) => void
}

/**
 * 한 계정 아래 여러 투자자를 두고 한 명씩 골라 작업하는 "현재 투자자".
 * 1명이면 자동 선택, 2명 이상이면 사용자가 직접 골라야 한다(저장된 선택이 목록에 없으면 무효).
 */
export function useCurrentInvestor(): CurrentInvestor {
  const { data, isLoading } = useInvestors()
  const stored = useSyncExternalStore(subscribe, readStored, () => null)
  const investors = data ?? []

  const select = useCallback((investorId: number) => {
    try {
      localStorage.setItem(STORAGE_KEY, String(investorId))
    } catch {
      // 저장 실패해도 이번 방문에서만 선택이 풀릴 뿐이다.
    }
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }, [])

  const current =
    investors.length === 1 ? investors[0] : (investors.find((i) => String(i.id) === stored) ?? null)

  return {
    investors,
    current,
    needsSelection: !isLoading && investors.length > 1 && current === null,
    isLoading,
    select,
  }
}
