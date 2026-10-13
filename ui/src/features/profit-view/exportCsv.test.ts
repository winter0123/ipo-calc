import { describe, it, expect } from 'vitest'
import { toProfitCsv, profitCsvFileName } from './exportCsv.ts'
import type { SaleProfitRow } from './useAllInvestorProfits.ts'

const row = (o: Partial<SaleProfitRow> = {}): SaleProfitRow =>
  ({
    saleId: 1,
    saleDate: '2026-09-21T10:00:00Z',
    quantity: 50,
    saleAmount: 2000000,
    matchedAcquisitionAmount: 1500000,
    saleFee: 5000,
    saleTax: 30000,
    realizedProfit: 366000,
    afterTaxProfit: 361000,
    ipoName: 'A공모주',
    investorName: '홍길동',
    ...o,
  }) as SaleProfitRow

const lines = (rows: SaleProfitRow[]) => toProfitCsv(rows).split('\r\n')

describe('toProfitCsv', () => {
  it('BOM, 열 순서, 합계 행', () => {
    const csv = toProfitCsv([row(), row({ saleId: 2, saleAmount: 100, afterTaxProfit: -8000 })])
    expect(csv.startsWith('﻿매도일,투자자,공모주명,수량,매도금액')).toBe(true)
    const l = lines([row(), row({ saleAmount: 100, afterTaxProfit: -8000 })])
    expect(l[1]).toBe('2026-09-21,홍길동,A공모주,50,2000000,1500000,5000,30000,366000,361000')
    expect(l[3]).toBe('합계,,,,2000100,3000000,10000,60000,732000,353000')
  })

  it('null은 빈칸, 음수는 그대로 숫자', () => {
    const l = lines([row({ saleAmount: null, ipoName: null, afterTaxProfit: -8000 })])
    expect(l[1]).toBe('2026-09-21,홍길동,,50,,1500000,5000,30000,366000,-8000')
  })

  it('이스케이프와 수식 주입 방지', () => {
    const l = lines([row({ ipoName: '=SUM(A1)', investorName: 'a,"b"' })])
    expect(l[1].startsWith('2026-09-21,"a,""b""",\'=SUM(A1),')).toBe(true)
    expect(lines([row({ ipoName: '-x' })])[1]).toContain(",'-x,")
  })
})

describe('profitCsvFileName', () => {
  it('기간/전체', () => {
    expect(profitCsvFileName('2026-10-01', '2026-10-13')).toBe('수익내역_2026-10-01_2026-10-13.csv')
    expect(profitCsvFileName('', '')).toBe('수익내역_전체.csv')
  })
})
