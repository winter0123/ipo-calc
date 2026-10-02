import { describe, it, expect } from 'vitest'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  grossGain,
  listingDateTime,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  resolveIpoPrice,
} from './saleMath.ts'

describe('saleMath', () => {
  it('공모주수수료 기본값은 2000원이다', () => {
    expect(DEFAULT_IPO_FEE).toBe(2000)
  })

  it('실현손익 = 판매단가×수량 − 공모가×수량 − 세금 및 기타 수수료', () => {
    // (15000 − 10000) × 30 − 1500 = 148500
    expect(realizedProfitFromFees(15000, 30, 10000, 1500)).toBe(148500)
    // 수수료가 없으면 차익 그대로
    expect(realizedProfitFromFees(15000, 30, 10000, 0)).toBe(150000)
    // 공모가보다 싸게 팔면 손실
    expect(realizedProfitFromFees(9000, 10, 10000, 100)).toBe(-10100)
  })

  it('실현손익에서 거꾸로 구한 세금 및 기타 수수료는 순방향 계산과 서로 역함수다', () => {
    const fees = otherFeesFromRealizedProfit(15000, 30, 10000, 148500)
    expect(fees).toBe(1500)
    expect(realizedProfitFromFees(15000, 30, 10000, fees)).toBe(148500)
    // 증권사 실현손익이 계산 차익보다 크면 음수(호출 쪽에서 오류 처리)
    expect(otherFeesFromRealizedProfit(15000, 30, 10000, 151000)).toBe(-1000)
  })

  it('최종수익금 = 실현손익 − 공모주수수료(공모주수수료는 실현손익에 넣지 않는다)', () => {
    expect(finalProfit(148500, 2000)).toBe(146500)
    expect(finalProfit(-10100, 2000)).toBe(-12100)
  })

  it('grossGain은 수수료 반영 전 차익이다', () => {
    expect(grossGain(15000, 30, 10000)).toBe(150000)
  })

  it('공모가는 확정 공모가 → 배정단가 순으로 쓰고 없으면 null', () => {
    expect(resolveIpoPrice(10000, 9000)).toBe(10000)
    expect(resolveIpoPrice(0, 9000)).toBe(9000)
    expect(resolveIpoPrice(null, 9000)).toBe(9000)
    expect(resolveIpoPrice(null, null)).toBeNull()
    expect(resolveIpoPrice(undefined, 0)).toBeNull()
  })

  it('상장일을 datetime-local 값(09:00)으로 바꾼다', () => {
    expect(listingDateTime('2026-10-23')).toBe('2026-10-23T09:00')
    expect(listingDateTime('2026-10-23T00:00:00Z')).toBe('2026-10-23T09:00')
    expect(listingDateTime(null)).toBeUndefined()
    expect(listingDateTime(undefined)).toBeUndefined()
  })
})
