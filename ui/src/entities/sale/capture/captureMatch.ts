import type { SaleRecord } from './captureParser.ts'

/** 청약을 고를 때 쓰는 최소 정보(entities 간 의존을 피하려고 필요한 모양만 정의한다). */
export interface MatchCandidate {
  subscriptionId: number
  /** 공모주 이름·회사명 후보 */
  names: (string | null | undefined)[]
  stockCode?: string | null
  /** 이미 등록된 매도(활성)의 날짜 'YYYY-MM-DD'와 수량 */
  sales: { date: string; quantity: number }[]
}

const normalize = (s: string) =>
  s
    .replace(/\(주\)|주식회사|㈜/g, '')
    .replace(/[^가-힣A-Za-z0-9]/g, '')
    .toLowerCase()

function lev(a: string, b: string): number {
  const d = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array<number>(b.length).fill(0)])
  for (let j = 1; j <= b.length; j++) d[0][j] = j
  for (let i = 1; i <= a.length; i++)
    for (let j = 1; j <= b.length; j++)
      d[i][j] = Math.min(
        d[i - 1][j] + 1,
        d[i][j - 1] + 1,
        d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1),
      )
  return d[a.length][b.length]
}

/** 같은 이름: 정규화 후 일치, 또는 4자 이상이고 숫자가 같을 때 OCR 한 글자 오인식(편집거리 1)까지 허용. */
function sameName(a: string, b: string): boolean {
  if (!a || !b) return false
  if (a === b) return true
  return (
    Math.min(a.length, b.length) >= 4 &&
    lev(a, b) <= 1 &&
    a.replace(/\D/g, '') === b.replace(/\D/g, '')
  )
}

/**
 * 캡처 기록과 맞는 청약 id 목록. 종목코드가 맞는 청약이 있으면 그것만, 없으면 이름이 같은 청약들.
 * 1건이면 확정, 여러 건(같은 공모주를 여러 계좌로 청약)이면 사용자가 고르게 한다.
 */
export function matchSubscriptions(rec: SaleRecord, cands: MatchCandidate[]): number[] {
  if (rec.stockCode) {
    const byCode = cands.filter((c) => c.stockCode === rec.stockCode)
    if (byCode.length) return byCode.map((c) => c.subscriptionId)
  }
  const name = normalize(rec.stockName)
  return cands
    .filter((c) => c.names.some((n) => n && sameName(name, normalize(n))))
    .map((c) => c.subscriptionId)
}

/** 같은 청약에 같은 날짜(·수량)의 매도가 이미 등록돼 있으면 true — 중복 등록 경고용. */
export function looksRegistered(rec: SaleRecord, cand: MatchCandidate): boolean {
  return cand.sales.some(
    (s) => s.date === rec.saleDate && (rec.quantity === undefined || s.quantity === rec.quantity),
  )
}

/** 폼에 넣을 값. 매도단가는 체결가, 없으면 매도금액/수량(나누어떨어질 때만). */
export interface CaptureFill {
  quantity?: number
  price?: number
  saleDate?: string
  /** 증권사 앱의 실현손익 */
  realizedProfit?: number
  /** 수수료 + 제세금 (둘 중 하나라도 읽혔을 때만) */
  fees?: number
}

export function captureToFill(rec: SaleRecord): CaptureFill {
  let price = rec.salePrice
  if (
    price === undefined &&
    rec.saleAmount !== undefined &&
    rec.quantity &&
    rec.saleAmount % rec.quantity === 0
  ) {
    price = rec.saleAmount / rec.quantity
  }
  return {
    quantity: rec.quantity,
    price,
    saleDate: rec.saleDate,
    realizedProfit: rec.realizedProfit,
    // KIS는 수수료+제세금을 따로, NH 목록은 제비용 합계만 준다
    fees:
      rec.fee !== undefined || rec.tax !== undefined ? (rec.fee ?? 0) + (rec.tax ?? 0) : rec.costs,
  }
}
