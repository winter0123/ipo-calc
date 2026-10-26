import { describe, expect, it } from 'vitest'
import { investmentHistoryKeys } from './queryKeys.ts'

describe('investmentHistoryKeys', () => {
  it('전체 페이지 key를 기존 목록 key의 하위 prefix로 만든다', () => {
    const filters = { investorId: 4 }
    expect(investmentHistoryKeys.allPages(filters)).toEqual([
      'investment-history',
      'list',
      filters,
      'all',
    ])
    expect(investmentHistoryKeys.allPages(filters).slice(0, 3)).toEqual(
      investmentHistoryKeys.list(filters),
    )
  })
})
