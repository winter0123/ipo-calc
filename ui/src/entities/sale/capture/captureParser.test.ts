import { describe, expect, it } from 'vitest'
import a from './fixtures/0_A_detail.json'
import b from './fixtures/1_B_list.json'
import c1 from './fixtures/2_C_set3_fee_date.json'
import c2 from './fixtures/3_C_set2_price_fee.json'
import c3 from './fixtures/4_C_set1_profit_qty.json'
import ba from './fixtures/browser_0_A_detail.json'
import bb from './fixtures/browser_1_B_list.json'
import bc1 from './fixtures/browser_2_C_set3_fee_date.json'
import bc2 from './fixtures/browser_3_C_set2_price_fee.json'
import bc3 from './fixtures/browser_4_C_set1_profit_qty.json'
import n0 from './fixtures/nh_0_list_qty_cost_settle.json'
import n1 from './fixtures/nh_1_list_profit_price_qty.json'
import n2 from './fixtures/nh_2_popup_trades.json'
import n3 from './fixtures/nh_3_popup_profit.json'
import bn0 from './fixtures/browser_nh_0_list_qty_cost_settle.json'
import bn1 from './fixtures/browser_nh_1_list_profit_price_qty.json'
import bn2 from './fixtures/browser_nh_2_popup_trades.json'
import bn3 from './fixtures/browser_nh_3_popup_profit.json'
import { mergeRecords, num, parse, verify, type SaleRecord } from './captureParser.ts'
import { captureToFill, looksRegistered, matchSubscriptions } from './captureMatch.ts'

interface Fixture {
  width: number
  height: number
  words: { t: string; x0: number; y0: number; x1: number; y1: number }[]
}
const fx = (j: Fixture) => parse(j.words, { width: j.width, height: j.height, fallbackYear: 2026 })

describe('캡처 파서', () => {
  it('A 종목 상세', () => {
    const p = fx(a)
    expect(p.kind).toBe('A_detail')
    expect({ ...p.records[0] }).toEqual({
      stockName: '글로벌테크놀로지',
      broker: 'KIS',
      stockCode: '486510',
      saleDate: '2026-09-29',
      realizedProfit: 77749,
      profitRate: 111.07,
      saleAmount: 148050,
      quantity: 7,
      salePrice: 21150,
      fee: 5,
      tax: 296,
      buyPrice: 10000,
    })
  })

  it('B 목록: 월 머리글 연도 보완', () => {
    const p = fx(b)
    expect(p.kind).toBe('B_list')
    expect(p.records.map((r) => [r.stockName, r.saleDate, r.realizedProfit])).toEqual([
      ['글로벌테크놀로지', '2026-09-29', 77749],
      ['한국제17호스팩', '2026-09-22', 11266],
      ['한국제16호스팩', '2026-06-30', 27064],
    ])
  })

  it('C 3장 병합', () => {
    const merged = mergeRecords([c1, c2, c3].map((j) => fx(j).records))
    expect(merged).toHaveLength(2)
    expect({ ...merged[1] }).toEqual({
      stockName: '한국제17호스팩',
      broker: 'KIS',
      fee: 0,
      tax: 54,
      saleDate: '2026-09-22',
      buyPrice: 2000,
      salePrice: 3415,
      realizedProfit: 11266,
      profitRate: 70.41,
      buyAmount: 0,
      saleAmount: 27320,
      quantity: 8,
    })
  })

  it('A + B 병합: 종목 상세가 목록 항목을 보강한다', () => {
    const merged = mergeRecords([fx(a).records, fx(b).records])
    expect(merged).toHaveLength(3)
    expect(merged[0]).toMatchObject({ stockCode: '486510', quantity: 7, realizedProfit: 77749 })
  })

  it('손실 부호와 쉼표 오인식', () => {
    for (const s of ['-1,234원', '−1,234', '△1,234', '▼1,234']) expect(num(s)).toBe(-1234)
    expect(num('27.320')).toBe(27320)
    expect(num('+5,000원')).toBe(5000)
  })

  it('검산으로 한 필드 오인식을 교정한다', () => {
    const ok: SaleRecord = {
      stockName: 'x',
      quantity: 7,
      salePrice: 21150,
      saleAmount: 148050,
      buyPrice: 10000,
      fee: 5,
      tax: 296,
      realizedProfit: 77749,
    }
    expect(verify({ ...ok, saleAmount: 143050 }).saleAmount).toBe(148050)
    expect(verify({ ...ok, salePrice: 21750 }).salePrice).toBe(21150)
    expect(verify({ ...ok, quantity: 1 }).quantity).toBe(7)
    expect(verify({ ...ok, quantity: 1 }).corrected).toEqual(['quantity'])
  })

  it('알아볼 수 없는 화면은 unknown', () => {
    const p = parse([{ t: '안녕', x0: 0, y0: 0, x1: 10, y1: 10 }], { width: 100, height: 100 })
    expect(p).toEqual({ kind: 'unknown', broker: 'unknown', records: [] })
  })
})

describe('NH투자증권', () => {
  const nh = () => [n0, n1, n2, n3].map(fx)

  it('증권사와 화면 종류를 판별한다', () => {
    expect(nh().map((p) => p.broker)).toEqual(['NH', 'NH', 'NH', 'NH'])
    expect(nh().map((p) => p.kind)).toEqual(['NH_list', 'NH_list', 'NH_detail', 'NH_detail'])
    for (const j of [a, b, c1, c2, c3]) expect(fx(j).broker).toBe('KIS')
  })

  it('목록: 종목 5건, 손실은 손익 부호를 따른다', () => {
    const [first, second] = nh()
    expect(first.records.map((r) => r.stockName)).toEqual([
      '토모큐브',
      '알지노믹스',
      '씨케이솔루션',
      '티엑스알로보틱스',
      '폴레드',
    ])
    expect(second.records.at(-1)).toMatchObject({ realizedProfit: -11905, profitRate: -14.05 })
  })

  it('팝업: 매매내역(잘린 열은 무시)', () => {
    const [, , p3, p4] = nh()
    expect({ ...p3.records[0] }).toEqual({
      stockName: '폴레드',
      saleDate: '2026-05-29',
      saleAmount: 72940,
      quantity: 14,
      tax: 145,
      buyAmount: 84700,
      realizedProfit: -11905,
      broker: 'NH',
      verified: true,
      buyPrice: 6050,
    })
    expect(p4.records[0].tax).toBeUndefined()
  })

  it('4장 병합: 폴레드는 손실 -11,905원, 검산 통과', () => {
    const m = mergeRecords(nh().map((p) => p.records))
    expect(m).toHaveLength(5)
    expect(m[4]).toMatchObject({
      stockName: '폴레드',
      quantity: 14,
      costs: 145,
      salePrice: 5210,
      saleDate: '2026-05-29',
      realizedProfit: -11905,
      verified: true,
    })
    expect(m.slice(0, 4).every((r) => r.realizedProfit! > 0)).toBe(true)
  })

  it('OCR이 손실의 -를 놓쳐도 손익률로 부호를 복구한다', () => {
    const r = {
      stockName: 'x',
      broker: 'NH' as const,
      saleAmount: 72940,
      costs: 145,
      realizedProfit: 11905,
      profitRate: 14.05,
    }
    const v = verify(r)
    expect([v.realizedProfit, v.profitRate, v.corrected]).toEqual([
      -11905,
      -14.05,
      ['realizedProfit'],
    ])
  })
})

describe('NH 브라우저 OCR 출력(PSM 6)', () => {
  it('팝업에서 종목명을 못 읽어도 실현손익이 같은 목록 기록에 붙는다', () => {
    const m = mergeRecords([bn0, bn1, bn2, bn3].map((j) => fx(j).records))
    expect(m.map((r) => r.stockName)).toEqual([
      '토모큐브',
      '알지노믹스',
      '씨케이솔루션',
      '티엑스알로보틱스',
      '폴레드',
    ])
    expect(m[4]).toMatchObject({
      quantity: 14,
      salePrice: 5210,
      saleDate: '2026-05-29',
      realizedProfit: -11905,
      verified: true,
    })
  })
})

describe('다른 증권사 화면', () => {
  it('종목명·매도수량 열이 있어도 한국투자증권 표가 아니면 unknown', () => {
    const words = ['종목명', '매도', '수량', '매수', '수량', '토모큐브', '13,450'].map((t, i) => ({
      t,
      x0: i * 50,
      y0: 100,
      x1: i * 50 + 40,
      y1: 130,
    }))
    expect(parse(words, { width: 1000, height: 2000 }).kind).toBe('unknown')
  })
})

describe('브라우저 OCR 출력(kor, canvas 이진화)', () => {
  it('A: 추·뭔 같은 오인식에도 같은 기록을 얻는다', () => {
    expect({ ...fx(ba).records[0] }).toMatchObject({
      stockCode: '486510',
      saleDate: '2026-09-29',
      quantity: 7,
      salePrice: 21150,
      saleAmount: 148050,
      fee: 5,
      tax: 296,
      buyPrice: 10000,
      realizedProfit: 77749,
    })
  })

  it('A+B+C 5장을 병합하면 종목별 완성 기록이 나온다', () => {
    const merged = mergeRecords([ba, bb, bc1, bc2, bc3].map((j) => fx(j).records))
    expect(merged.map((r) => r.stockName)).toEqual([
      '글로벌테크놀로지',
      '한국제17호스팩',
      '한국제16호스팩',
    ])
    expect(merged[1]).toMatchObject({
      saleDate: '2026-09-22',
      quantity: 8,
      salePrice: 3415,
      saleAmount: 27320,
      fee: 0,
      tax: 54,
      buyPrice: 2000,
      realizedProfit: 11266,
    })
    expect(merged[2]).toMatchObject({ saleDate: '2026-06-30', realizedProfit: 27064 })
  })
})

describe('청약 매칭과 폼 값', () => {
  const cands = [
    {
      subscriptionId: 1,
      names: ['글로벌테크놀로지', '(주)글로벌테크놀로지'],
      stockCode: '486510',
      sales: [],
    },
    {
      subscriptionId: 2,
      names: ['한국제17호스팩'],
      stockCode: null,
      sales: [{ date: '2026-09-22', quantity: 8 }],
    },
    { subscriptionId: 3, names: ['한국제16호스팩'], stockCode: null, sales: [] },
  ]

  it('종목코드 우선, 없으면 이름(OCR 한 글자 오류 허용, 숫자는 불허)', () => {
    expect(matchSubscriptions({ stockName: '엉뚱', stockCode: '486510' }, cands)).toEqual([1])
    expect(matchSubscriptions({ stockName: '한국제17호스팩' }, cands)).toEqual([2])
    expect(matchSubscriptions({ stockName: '한국제17호스백' }, cands)).toEqual([2])
    expect(matchSubscriptions({ stockName: '모르는회사' }, cands)).toEqual([])
  })

  it('이미 등록된 매도를 알아본다', () => {
    const rec = { stockName: '한국제17호스팩', saleDate: '2026-09-22', quantity: 8 }
    expect(looksRegistered(rec, cands[1])).toBe(true)
    expect(looksRegistered({ ...rec, quantity: 5 }, cands[1])).toBe(false)
    expect(looksRegistered(rec, cands[2])).toBe(false)
  })

  it('매도단가는 체결가, 없으면 매도금액/수량, 세금 및 기타는 수수료+제세금', () => {
    expect(
      captureToFill({ stockName: 'x', salePrice: 21150, fee: 5, tax: 296, quantity: 7 }),
    ).toMatchObject({ price: 21150, fees: 301 })
    expect(captureToFill({ stockName: 'x', saleAmount: 27320, quantity: 8 }).price).toBe(3415)
    expect(captureToFill({ stockName: 'x', saleAmount: 27321, quantity: 8 }).price).toBeUndefined()
    expect(captureToFill({ stockName: 'x' }).fees).toBeUndefined()
    expect(captureToFill({ stockName: 'x', costs: 145 }).fees).toBe(145)
  })
})
