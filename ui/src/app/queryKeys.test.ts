import { describe, it, expect } from 'vitest'
import { investorKeys } from '../entities/investor/index.ts'
import { securitiesAccountKeys } from '../entities/securities-account/index.ts'
import { investmentHistoryKeys } from '../entities/subscription/index.ts'
import { profitKeys } from '../entities/profit/index.ts'
import { dashboardKeys } from '../entities/dashboard/index.ts'

describe('query key factory', () => {
  it('모든 키는 kebab-case 네임스페이스로 시작한다', () => {
    const roots = [
      investorKeys.all,
      securitiesAccountKeys.all,
      investmentHistoryKeys.all,
      profitKeys.all,
      dashboardKeys.all,
    ].map(([root]) => root)
    expect(roots).toEqual([
      'investors',
      'securities-accounts',
      'investment-history',
      'profit',
      'dashboard',
    ])
  })

  it('세부 키는 all 접두사를 공유해 prefix invalidation이 가능하다', () => {
    expect(investmentHistoryKeys.list({ investorId: 1 }).slice(0, 1)).toEqual(
      investmentHistoryKeys.all,
    )
    expect(investmentHistoryKeys.detail(3).slice(0, 1)).toEqual(investmentHistoryKeys.all)
    expect(dashboardKeys.byPeriod('month', 2026, 9).slice(0, 1)).toEqual(dashboardKeys.all)
  })

  it('기간 키는 undefined 요소를 생략해 unit 접두사로 묶인다', () => {
    expect(dashboardKeys.byPeriod('year')).toEqual(['dashboard', 'by-period', 'year'])
    expect(dashboardKeys.byPeriod('year', 2026)).toEqual(['dashboard', 'by-period', 'year', 2026])
  })
})
