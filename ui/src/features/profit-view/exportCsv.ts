import type { SaleProfitRow } from './useAllInvestorProfits.ts'

const HEADER = [
  '매도일',
  '투자자',
  '공모주명',
  '수량',
  '매도금액',
  '취득금액',
  '공모주수수료',
  '세금 및 기타',
  '실현손익',
  '최종수익금',
]

// 문자열 셀: 수식 주입 방지('), 쉼표·따옴표·줄바꿈은 따옴표로 감싼다.
function text(value: string | null | undefined): string {
  if (!value) return ''
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe
}

// 숫자 셀: 쉼표 없는 정수(음수 그대로), null은 빈칸.
const num = (value: number | null | undefined): string => (value == null ? '' : String(value))

/** 화면에 로드된 매도 행 + 합계 행을 엑셀용 CSV(UTF-8 BOM)로 만든다. */
export function toProfitCsv(rows: SaleProfitRow[]): string {
  const sum = (pick: (s: SaleProfitRow) => number | null) =>
    rows.reduce((acc, s) => acc + (pick(s) ?? 0), 0)
  const lines = [
    HEADER.map(text),
    ...rows.map((s) => [
      text(s.saleDate.slice(0, 10)),
      text(s.investorName),
      text(s.ipoName),
      num(s.quantity),
      num(s.saleAmount),
      num(s.matchedAcquisitionAmount),
      num(s.saleFee),
      num(s.saleTax),
      num(s.realizedProfit),
      num(s.afterTaxProfit),
    ]),
    [
      '합계',
      '',
      '',
      '',
      num(sum((s) => s.saleAmount)),
      num(sum((s) => s.matchedAcquisitionAmount)),
      num(sum((s) => s.saleFee)),
      num(sum((s) => s.saleTax)),
      num(sum((s) => s.realizedProfit)),
      num(sum((s) => s.afterTaxProfit)),
    ],
  ]
  return '﻿' + lines.map((l) => l.join(',')).join('\r\n') + '\r\n'
}

export function profitCsvFileName(from: string, to: string): string {
  return from && to ? `수익내역_${from}_${to}.csv` : '수익내역_전체.csv'
}

export function downloadCsv(csv: string, fileName: string): void {
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
