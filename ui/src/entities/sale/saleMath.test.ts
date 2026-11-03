import { describe, it, expect } from 'vitest'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  grossGain,
  listingDate,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  acquisitionAmountForSale,
  allocationUnitPrice,
  resolveIpoPrice,
  toSalePayload,
} from './saleMath.ts'

describe('saleMath', () => {
  it('공모주수수료 기본값은 2000원이다', () => {
    expect(DEFAULT_IPO_FEE).toBe(2000)
  })

  it('acquisitionAmountForSale: allocatedAmount가 있으면 안분 계산(반올림)', () => {
    // (a) 95,000 / 10주 중 5주 매도 → 47,500
    expect(acquisitionAmountForSale(5, 10000, 95000, 10)).toBe(47500)
    // (b) 100 / 3주 중 2주 매도 → roundHalfUp(100*2/3) = 67
    expect(acquisitionAmountForSale(2, 10000, 100, 3)).toBe(67)
    // allocatedQuantity가 0이면 0
    expect(acquisitionAmountForSale(5, 10000, 100, 0)).toBe(0)
    // allocatedQuantity가 음수면 0
    expect(acquisitionAmountForSale(5, 10000, 100, -1)).toBe(0)
  })

  it('acquisitionAmountForSale: allocatedAmount 없으면 fixedPrice × 수량', () => {
    // (c) allocatedAmount null, fixedPrice 10,000, 매도 5주 → 50,000
    expect(acquisitionAmountForSale(5, 10000, null, 10)).toBe(50000)
    expect(acquisitionAmountForSale(5, 10000, undefined, 10)).toBe(50000)
    expect(acquisitionAmountForSale(0, 10000, null, 10)).toBe(0)
  })

  it('acquisitionAmountForSale: 둘 다 없으면 0', () => {
    expect(acquisitionAmountForSale(5, null, null, 10)).toBe(0)
    expect(acquisitionAmountForSale(5, undefined, undefined, undefined)).toBe(0)
  })

  it('allocationUnitPrice: 배정 단가(주당) 계산', () => {
    expect(allocationUnitPrice(10000, 95000, 10)).toBe(9500)
    expect(allocationUnitPrice(10000, 100, 3)).toBe(33)
    expect(allocationUnitPrice(10000, null, 10)).toBe(10000)
    expect(allocationUnitPrice(null, 95000, 10)).toBe(9500)
    expect(allocationUnitPrice(null, null, 10)).toBeNull()
    expect(allocationUnitPrice(10000, 100, 0)).toBe(10000)
  })

  it('실현손익 = 매도금액 − 취득금액 − 세금 및 기타 수수료', () => {
    // allocatedAmount 있는 경우
    expect(realizedProfitFromFees(15000, 30, 10000, 1500, 95000, 10)).toBe(450000 - 285000 - 1500)
    // allocatedAmount 없는 경우(fixedPrice 사용)
    expect(realizedProfitFromFees(15000, 30, 10000, 1500)).toBe(450000 - 300000 - 1500)
    // 수수료가 없으면 차익 그대로
    expect(realizedProfitFromFees(15000, 30, 10000, 0, 95000, 10)).toBe(450000 - 285000)
    // 공모가보다 싸게 팔면 손실
    expect(realizedProfitFromFees(9000, 10, 10000, 100)).toBe(90000 - 100000 - 100)
  })

  it('실현손익에서 거꾸로 구한 세금 및 기타 수수료는 순방향 계산과 서로 역함수다', () => {
    const fees = otherFeesFromRealizedProfit(15000, 30, 10000, 148500)
    expect(fees).toBe(1500)
    expect(realizedProfitFromFees(15000, 30, 10000, fees)).toBe(148500)
    // allocatedAmount 있는 경우도 역함수
    const fees2 = otherFeesFromRealizedProfit(15000, 30, 10000, 163500, 95000, 10)
    expect(fees2).toBe(1500)
    expect(realizedProfitFromFees(15000, 30, 10000, fees2, 95000, 10)).toBe(163500)
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

  it('resolveIpoPrice: 확정 공모가가 0보다 크면 그 값, 없으면 null', () => {
    expect(resolveIpoPrice(10000)).toBe(10000)
    expect(resolveIpoPrice(0)).toBeNull()
    expect(resolveIpoPrice(null)).toBeNull()
    expect(resolveIpoPrice(undefined)).toBeNull()
  })

  it('상장일을 datetime-local 값(09:00)으로 바꾼다', () => {
    expect(listingDate('2026-10-23')).toBe('2026-10-23')
    expect(listingDate('2026-10-23T00:00:00Z')).toBe('2026-10-23')
    expect(listingDate(null)).toBeUndefined()
    expect(listingDate(undefined)).toBeUndefined()
  })

  it('저장 매핑: amount=단가×수량, 날짜에 T09:00, 빈 수수료(NaN)는 0', () => {
    expect(
      toSalePayload({
        quantity: 30,
        price: 15000,
        saleDate: '2026-10-23',
        ipoFee: 2000,
        otherFees: 1500,
      }),
    ).toEqual({
      quantity: 30,
      price: 15000,
      amount: 450000,
      saleDate: '2026-10-23T09:00',
      fee: 2000,
      tax: 1500,
    })
    const blank = toSalePayload({
      quantity: 1,
      price: 100,
      saleDate: '2026-10-23',
      ipoFee: NaN,
      otherFees: NaN,
    })
    expect(blank.fee).toBe(0)
    expect(blank.tax).toBe(0)
  })

  it('음수 단가·공모가 없음(null)도 계산식 그대로 다룬다', () => {
    expect(
      toSalePayload({ quantity: 2, price: -5, saleDate: '2026-10-23', ipoFee: 0, otherFees: 0 })
        .amount,
    ).toBe(-10)
    expect(resolveIpoPrice(undefined)).toBeNull()
    expect(finalProfit(-500, 2000)).toBe(-2500)
  })
})
