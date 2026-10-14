// 한국투자증권 앱 캡처(OCR 단어 + bbox) -> 매도 기록 파서. 순수 함수, 의존성 없음.

export type Word = { t: string; x0: number; y0: number; x1: number; y1: number }
export type Kind = 'A_detail' | 'B_list' | 'C_table' | 'NH_list' | 'NH_detail' | 'unknown'
export type Broker = 'KIS' | 'NH' | 'unknown'
export type SaleRecord = {
  stockName: string
  stockCode?: string
  saleDate?: string
  quantity?: number
  salePrice?: number
  saleAmount?: number
  fee?: number
  tax?: number
  realizedProfit?: number
  buyPrice?: number
  profitRate?: number
  buyAmount?: number
  corrected?: string[] // 검산으로 교정된 필드
  broker?: Broker
  costs?: number // NH 목록: 제비용(수수료+제세금 합계, 분리 불가)
  verified?: boolean // NH: 매도금액-매입금액-수수료-제세금=실현손익 검산 결과(입력이 다 있을 때만)
}
export type Parsed = { kind: Kind; broker: Broker; records: SaleRecord[] }
type Opts = { width: number; height: number; fallbackYear?: number }

const NEG = '-−–—△▼▽'
const isSign = (s: string) => s.length === 1 && (NEG + '+▲').includes(s)

// ---------- 숫자 ----------
/** "77,749원" "-1,234" "△5,000" "27.320"(쉼표가 점으로 오인식) -> number. 실패 시 undefined */
export function num(raw: string | undefined): number | undefined {
  if (!raw) return undefined
  let s = raw.replace(/\s+/g, '')
  const neg = new RegExp(`^[(]?[${NEG}]`).test(s) || /^\(\d/.test(s)
  s = s
    .replace(/[^\dOoIl,.]/g, '')
    .replace(/[Oo]/g, '0')
    .replace(/[Il]/g, '1')
  if (!/\d/.test(s)) return undefined
  s = s.replace(/\.(?=\d{3}(\D|$))/g, ',') // 27.320 -> 27,320 (금액 문맥 전용)
  s = s.replace(/,/g, '')
  if (!/^\d+$/.test(s)) return undefined
  const v = Number(s)
  return neg ? -v : v
}
function pct(raw: string): number | undefined {
  const m = raw.match(new RegExp(`([${NEG}+▲]?)\\s*(\\d+(?:\\.\\d+)?)\\s*%`))
  if (!m) return undefined
  return (NEG.includes(m[1]) && m[1] ? -1 : 1) * Number(m[2])
}
// '원'은 브라우저 OCR에서 뭔·윈·웬·왼으로 읽히기도 한다
const WON = '[원뭔윈웬왼]'
const moneyRe = new RegExp(`([${NEG}+▲]?)\\s*([\\dOo][\\dOo,.]*)\\s*${WON}`, 'g')
function moneys(line: string): number[] {
  return [...line.matchAll(moneyRe)]
    .map((m) => num((m[1] === '+' || m[1] === '▲' ? '' : m[1]) + m[2])!)
    .filter((v) => v !== undefined)
}

// ---------- 라인 구성 ----------
type Line = { text: string; compact: string; y: number; words: Word[] }
const ascii = (c: string) => /[A-Za-z0-9.,%+-]/.test(c)
function buildLines(words: Word[]): Line[] {
  const ws = words.filter((w) => w.t.trim()).sort((a, b) => a.y0 + a.y1 - (b.y0 + b.y1))
  const lines: Word[][] = []
  for (const w of ws) {
    const yc = (w.y0 + w.y1) / 2
    const cur = lines[lines.length - 1]
    if (cur) {
      const cy = cur.reduce((s, v) => s + (v.y0 + v.y1) / 2, 0) / cur.length
      const h = Math.min(w.y1 - w.y0, ...cur.map((v) => v.y1 - v.y0))
      if (Math.abs(yc - cy) <= 0.5 * h) {
        cur.push(w)
        continue
      }
    }
    lines.push([w])
  }
  return lines.map((l) => {
    l.sort((a, b) => a.x0 - b.x0)
    let text = ''
    l.forEach((w, i) => {
      if (i) {
        const gap = w.x0 - l[i - 1].x1
        const bothAscii = ascii(text.slice(-1)) && ascii(w.t[0])
        text += gap > 0.8 * (w.y1 - w.y0) || bothAscii ? ' ' : ''
      }
      text += w.t
    })
    return { text, compact: text.replace(/\s+/g, ''), y: (l[0].y0 + l[0].y1) / 2, words: l }
  })
}

/** 종목명 정리: 로고 문자·기호 제거 */
function cleanName(s: string): string {
  return s
    .replace(/[^가-힣A-Za-z0-9&·\s]/g, ' ')
    .replace(/^\s*[A-Za-z]\s+(?=[가-힣])/, '')
    .replace(/\s+/g, '')
    .trim()
}
const hangul = (s: string) => /[가-힣]/.test(s)
const iso = (y: number, m: string, d: string) => `${y}-${m}-${d}`
const GUBUN = /^(현금|연금|신용|융자|담보|대주|현)$/

// ---------- 판별 ----------
export function detectKind(lines: Line[]): Kind {
  const all = lines.map((l) => l.compact).join('\n')
  if (/매도주식수|체결가격|평균매수/.test(all)) return 'A_detail'
  if (/매매내역|종목명/.test(all) && /두줄|대출일/.test(all)) return 'C_table'
  if (/\d{2}\.\d{2}\.?[현연]금|\d{2}\.\d{2}\.(신용|융자)/.test(all) || /\d{2}년\d{2}월/.test(all))
    return 'B_list'
  return 'unknown'
}

// ---------- A: 종목 상세 ----------
function parseA(lines: Line[]): SaleRecord[] {
  const r: SaleRecord = { stockName: '' }
  const idxCode = lines.findIndex(
    (l) => /^\D{0,3}\d{6}$/.test(l.compact) || /^\d{6}$/.test(l.compact),
  )
  if (idxCode >= 0) {
    r.stockCode = lines[idxCode].compact.match(/\d{6}/)![0]
    for (let i = idxCode - 1; i >= 0 && i >= idxCode - 2; i--)
      if (hangul(lines[i].text) && !/실현손익|한국|미국/.test(lines[i].compact)) {
        r.stockName = cleanName(lines[i].text)
        break
      }
  }
  for (const l of lines) {
    const c = l.compact
    const m = moneys(l.text)
    const dm = l.text.match(/(\d{4})\s*\.\s*(\d{2})\s*\.\s*(\d{2})/)
    if (dm && !r.saleDate) r.saleDate = iso(+dm[1], dm[2], dm[3])
    else if (/실현손익/.test(c) && m.length) {
      r.realizedProfit = m[0]
      r.profitRate = pct(l.text)
    } else if (/주식수|매도주/.test(c)) {
      const q = c.match(/(?:주식수|매도주)[^\dOo]*([\dOo]+)/)
      if (q) r.quantity = num(q[1])
    } // '주'는 추·쭈로도 읽혀 라벨 뒤 첫 숫자를 쓴다
    else if (/평균|매수가/.test(c) && m.length) r.buyPrice = m[m.length - 1]
    else if (/체결/.test(c) && m.length) r.salePrice = m[m.length - 1]
    else if (/매도금액|매도금/.test(c) && m.length) r.saleAmount = m[m.length - 1]
    else if (/^수수/.test(c) && m.length) r.fee = m[m.length - 1]
    else if (/제세/.test(c) && m.length) r.tax = m[m.length - 1]
  }
  return r.stockName ? [r] : []
}

// ---------- B: 실현손익 목록 ----------
function parseB(lines: Line[], opt: Opts): SaleRecord[] {
  const out: SaleRecord[] = []
  let year = opt.fallbackYear,
    started = false
  let cur: SaleRecord | null = null
  for (const l of lines) {
    const c = l.compact
    // 월 머리글: "26년09월", OCR 변형 "26H06"
    if (!/[원%.]/.test(c) && /^\D{0,2}(\d{2})\D{0,2}(\d{2})\D{0,2}$/.test(c) && c.length <= 8) {
      const m = c.match(/(\d{2})\D{0,2}(\d{2})/)!
      year = 2000 + +m[1]
      started = true
      cur = null
      continue
    }
    if (!started) continue
    const dm = c.match(/(\d{2})\.(\d{2})\.?\s*[현연신융]/) ?? c.match(/^\D?(\d{2})\.(\d{2})\./)
    const hasAmt = new RegExp(WON).test(c)
    if (hasAmt && hangul(l.text) && !dm) {
      // 종목명 + 손익금액
      const name = cleanName(l.text.replace(moneyRe, ' '))
      const m = moneys(l.text)
      if (name && m.length) {
        cur = { stockName: name, realizedProfit: m[0] }
        out.push(cur)
      }
    } else if (dm && cur) {
      if (year) cur.saleDate = iso(year, dm[1], dm[2])
      cur.profitRate = pct(l.text)
    }
  }
  return out
}

// ---------- C: 매매내역 표(bbox 열 매핑) ----------
function parseC(lines: Line[], words: Word[], opt: Opts): SaleRecord[] {
  const W = opt.width,
    H = opt.height,
    leftMax = 0.285 * W
  const hdr = lines.filter(
    (l) => /구분|종목명|손익률|대출일/.test(l.compact) && l.words[0].x0 < 0.2 * W,
  )
  const hdrBottom = hdr.length
    ? Math.max(...hdr.map((l) => Math.max(...l.words.map((w) => w.y1))))
    : 0.45 * H
  // 앵커: 왼쪽 열의 종목명 줄 / 구분 줄
  type Anchor = { name: string; yTop: number; yBot?: number }
  const anchors: Anchor[] = []
  for (const l of lines) {
    const left = l.words.filter((w) => w.x1 <= leftMax + 10 && w.y0 > hdrBottom - 5)
    if (!left.length) continue
    const text = left.map((w) => w.t).join('')
    if (GUBUN.test(text.replace(/\s/g, ''))) {
      const a = anchors[anchors.length - 1]
      if (a && a.yBot === undefined) a.yBot = l.y
      continue
    }
    if (hangul(text) && text.length >= 3 && !/메뉴|주문|잔고|이체|지수/.test(text))
      anchors.push({ name: cleanName(text), yTop: l.y })
  }
  // 하단 탭바 제외
  const valid = anchors.filter((a) => a.yTop < 0.88 * H)
  // 숫자 토큰(부호 단독 단어는 다음 단어에 붙임)
  const toks: { t: string; x1: number; y: number }[] = []
  const sorted = [...words].sort((a, b) => a.y0 - b.y0 || a.x0 - b.x0)
  for (let i = 0; i < sorted.length; i++) {
    let w = sorted[i]
    if (w.x0 < leftMax) continue
    let t = w.t
    if (
      isSign(t) &&
      sorted[i + 1] &&
      Math.abs(sorted[i + 1].y0 - w.y0) < 15 &&
      sorted[i + 1].x0 - w.x1 < 40
    ) {
      t += sorted[i + 1].t
      w = { ...sorted[i + 1] }
      i++
    }
    if (/^[-−–△▼▽+▲]?[\dOo][\dOo,.]*%?$/.test(t)) toks.push({ t, x1: w.x1, y: (w.y0 + w.y1) / 2 })
  }
  type Cell = { top?: string; bot?: string }
  const rows = valid.map((a, i) => {
    const yBot = a.yBot ?? a.yTop + 0.027 * H
    const lo = a.yTop - 0.03 * H,
      hi = Math.max(yBot, a.yTop) + 0.03 * H
    const next = valid[i + 1] ? valid[i + 1].yTop - 0.03 * H : Infinity
    const mine = toks.filter((k) => k.y >= lo && k.y <= Math.min(hi, next) && k.y > hdrBottom)
    return { a, yBot, mine }
  })
  // x1 군집 = 열
  const xs = rows.flatMap((r) => r.mine.map((k) => k.x1)).sort((p, q) => p - q)
  const centers: number[] = []
  for (const x of xs) {
    if (!centers.length || x - centers[centers.length - 1] > 0.05 * W) centers.push(x)
    else centers[centers.length - 1] = x
  }
  const colOf = (x: number) =>
    centers.reduce((b, c, i) => (Math.abs(c - x) < Math.abs(centers[b] - x) ? i : b), 0)
  const grid = rows.map((r) => {
    const cells: Cell[] = centers.map(() => ({}))
    const mid = (r.a.yTop + r.yBot) / 2
    for (const k of r.mine) {
      const c = cells[colOf(k.x1)]
      if (k.y < mid) c.top = c.top ?? k.t
      else c.bot = c.bot ?? k.t
    }
    return cells
  })
  const colHas = (f: (c: Cell) => boolean) => centers.map((_, ci) => grid.some((g) => f(g[ci])))
  const isPct = colHas((c) => /%/.test(c.bot ?? '') || /%/.test(c.top ?? ''))
  const isDate = colHas(
    (c) => /^\d{2}\.\d{2}\.\d{2}$/.test(c.top ?? '') || /^\d{2}\.\d{2}\.\d{2}$/.test(c.bot ?? ''),
  )
  const hdrText = lines
    .filter((l) => l.y < hdrBottom && l.y > hdrBottom - 0.07 * H)
    .map((l) => l.compact)
    .join('')
  const hasPriceHdr = /단가|수단|도단/.test(hdrText)

  return rows.map((r, ri): SaleRecord => {
    const rec: SaleRecord = { stockName: r.a.name }
    const g = grid[ri]
    const ci = centers.map((_, i) => i)
    const pctCol = ci.find((i) => isPct[i])
    const dateCol = ci.find((i) => isDate[i])
    if (pctCol !== undefined) {
      // 세트1: 실현손익/손익률, 매수금액/매도금액, 매수수량/매도수량
      const [c1, c2, c3] = [pctCol, pctCol + 1, pctCol + 2].map((i) => g[i])
      rec.realizedProfit = num(c1?.top)
      rec.profitRate = c1?.bot ? pct(c1.bot) : undefined
      rec.buyAmount = num(c2?.top)
      rec.saleAmount = num(c2?.bot)
      if (c3) rec.quantity = num(c3.bot)
    } else {
      const rest = ci.filter((i) => i !== dateCol)
      const hasPrice = hasPriceHdr || (rest.length >= 2 && dateCol === undefined)
      let k = 0
      if (hasPrice && rest.length >= 1) {
        const c = g[rest[k++]]
        rec.buyPrice = num(c.top)
        rec.salePrice = num(c.bot)
      }
      if (rest.length > k) {
        const c = g[rest[k++]]
        rec.fee = num(c.top)
        rec.tax = num(c.bot)
      }
      if (dateCol !== undefined) {
        const d = (g[dateCol].top ?? g[dateCol].bot ?? '').match(/(\d{2})\.(\d{2})\.(\d{2})/)
        if (d) rec.saleDate = iso(2000 + +d[1], d[2], d[3])
      }
    }
    return rec
  })
}

/** 증권사 판별: 화면 고유 문구(NH: 제비용·정산금액·평균단가·종목별 매매내역·손익금액·체결일 기준. '주식잔고·매매손익'은 KIS C 화면에도 있어 쓰지 않는다) */
export function detectBroker(lines: Line[]): Broker {
  const all = lines.map((l) => l.compact).join('\n')
  if (/제비용|정산금액|평균단가|종목별매매내역|손익금액|체결일기준/.test(all)) return 'NH'
  if (
    /매도주식수|체결가격|평균매수|매매내역|종목명|대출일/.test(all) ||
    /\d{2}\.\d{2}\.?[현연]금|\d{2}년\d{2}월/.test(all)
  )
    return 'KIS'
  return 'unknown'
}

export function parse(words: Word[], opt: Opts): Parsed {
  const lines = buildLines(words)
  const broker = detectBroker(lines)
  if (broker === 'NH') {
    const all = lines.map((l) => l.compact).join('\n')
    const popup = /종목별매매내역|손익금액|매매구분/.test(all)
    const records = popup ? parseNhDetail(lines, words, opt) : parseNhList(lines, words, opt)
    return {
      kind: popup ? 'NH_detail' : 'NH_list',
      broker,
      records: records.map((r) => clean(verify({ ...r, broker }))),
    }
  }
  const kind = detectKind(lines)
  const records =
    kind === 'A_detail'
      ? parseA(lines)
      : kind === 'B_list'
        ? parseB(lines, opt)
        : kind === 'C_table'
          ? parseC(lines, words, opt)
          : []
  return {
    kind,
    broker,
    records: records.map((r) =>
      verify({ ...r, broker: broker === 'unknown' ? undefined : broker }),
    ),
  }
}

// ---------- 병합 ----------
/** 여러 장의 records를 종목명(+날짜가 둘 다 있으면 날짜) 기준으로 병합. 같은 장 안의 같은 종목은 합치지 않는다. */
export function mergeRecords(sets: SaleRecord[][]): SaleRecord[] {
  const out: { rec: SaleRecord; src: Set<number> }[] = []
  sets.forEach((set, si) => {
    for (const r of set) {
      const hit = out.find(
        (o) =>
          !o.src.has(si) &&
          (sameName(o.rec.stockName, r.stockName) ||
            // 종목명을 못 읽은 기록은 실현손익이 같은 기록에 붙인다
            ((!o.rec.stockName || !r.stockName) &&
              o.rec.realizedProfit !== undefined &&
              o.rec.realizedProfit === r.realizedProfit)) &&
          (!o.rec.saleDate || !r.saleDate || o.rec.saleDate === r.saleDate),
      )
      if (hit) {
        const target = hit.rec as Record<string, unknown>
        for (const [k, v] of Object.entries(r))
          if (v !== undefined && k !== 'corrected' && (target[k] === undefined || target[k] === ''))
            target[k] = v
        hit.src.add(si)
      } else out.push({ rec: { ...r }, src: new Set([si]) })
    }
  })
  return out.filter((o) => o.rec.stockName).map((o) => verify(o.rec))
}
function lev(a: string, b: string): number {
  const d = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array(b.length).fill(0)])
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
// ponytail: 종목명 편집거리 1 허용(OCR 한 글자 오인식). 다른 종목과 1글자만 다른 경우(한국제16/17호스팩)는 숫자 때문에 구분 못함 -> 숫자 차이는 불허.
const sameName = (a: string, b: string) =>
  a === b || (lev(a, b) <= 1 && a.replace(/\D/g, '') === b.replace(/\D/g, ''))

// ---------- 검산 / 자동 교정 ----------
/**
 * E1: 수량*체결가 = 매도금액
 * E2: 매도금액 - 수량*매수가 - 수수료 - 제세금 = 실현손익
 * 모든 식이 성립하도록 필드 하나만 바꾸는 해가 있고(자릿수 편집거리가 가장 가까운 후보) 유일하면 교정.
 */
type F = 'quantity' | 'salePrice' | 'saleAmount' | 'buyPrice' | 'fee' | 'tax' | 'realizedProfit'
const FIELDS: F[] = [
  'quantity',
  'salePrice',
  'saleAmount',
  'buyPrice',
  'fee',
  'tax',
  'realizedProfit',
]
export function holds(r: SaleRecord) {
  const e1 =
    r.quantity !== undefined && r.salePrice !== undefined && r.saleAmount !== undefined
      ? r.quantity * r.salePrice === r.saleAmount
      : undefined
  const e2 = [r.saleAmount, r.quantity, r.buyPrice, r.fee, r.tax, r.realizedProfit].every(
    (v) => v !== undefined,
  )
    ? r.saleAmount! - r.quantity! * r.buyPrice! - r.fee! - r.tax! === r.realizedProfit
    : undefined
  return [e1, e2]
}
export function verify(r: SaleRecord): SaleRecord {
  if (r.broker === 'NH') return verifyNh(r)
  const [e1, e2] = holds(r)
  if (e1 !== false && e2 !== false) return r
  const cands: { f: F; v: number }[] = []
  for (const f of FIELDS) {
    const vals = new Set<number>()
    const t = { ...r }
    const q = r.quantity,
      p = r.salePrice,
      S = r.saleAmount,
      b = r.buyPrice,
      fee = r.fee,
      tax = r.tax,
      P = r.realizedProfit
    if (f === 'saleAmount') {
      if (q !== undefined && p !== undefined) vals.add(q * p)
      if ([q, b, fee, tax, P].every((x) => x !== undefined)) vals.add(P! + q! * b! + fee! + tax!)
    }
    if (f === 'salePrice' && q && S !== undefined) vals.add(S / q)
    if (f === 'quantity') {
      if (p && S !== undefined) vals.add(S / p)
      if (b && [S, fee, tax, P].every((x) => x !== undefined)) vals.add((S! - fee! - tax! - P!) / b)
    }
    if (f === 'buyPrice' && q && [S, fee, tax, P].every((x) => x !== undefined))
      vals.add((S! - fee! - tax! - P!) / q)
    if (f === 'fee' && [S, q, b, tax, P].every((x) => x !== undefined))
      vals.add(S! - q! * b! - tax! - P!)
    if (f === 'tax' && [S, q, b, fee, P].every((x) => x !== undefined))
      vals.add(S! - q! * b! - fee! - P!)
    if (f === 'realizedProfit' && [S, q, b, fee, tax].every((x) => x !== undefined))
      vals.add(S! - q! * b! - fee! - tax!)
    for (const v of vals) {
      if (!Number.isInteger(v) || (v < 0 && f !== 'realizedProfit')) continue
      t[f] = v
      const [a1, a2] = holds(t)
      if (a1 !== false && a2 !== false && (a1 || a2) && v !== r[f]) cands.push({ f, v })
    }
  }
  if (!cands.length) return r
  const dist = (c: { f: F; v: number }) => lev(String(r[c.f] ?? ''), String(c.v))
  cands.sort((x, y) => dist(x) - dist(y))
  if (cands.length > 1 && dist(cands[0]) === dist(cands[1])) return r // 모호 -> 교정 안 함
  return { ...r, [cands[0].f]: cands[0].v, corrected: [...(r.corrected ?? []), cands[0].f] }
}

// =====================================================================
// NH투자증권 (앱: 주식 잔고·손익 > 매매손익 > 종목별 / 종목별 매매내역 팝업)
// 손실 표시: 숫자 앞 '-' + 파란색. 이익은 부호 없이 빨간색. 손익률은 목록에서 부호가 없고(파란색만) 팝업에서만 '-'.
// -> 손익률 부호는 실현손익 부호를 따른다.
// ---------------------------------------------------------------------
const NUMTOK = /^[-−–△▼▽+▲]?[\dOo][\dOo,.]*%?$/
/** 부호만 따로 인식된 단어를 다음 숫자 단어에 붙여 숫자 토큰으로 만든다 */
function numTokens(words: Word[], minX = 0): { t: string; x0: number; x1: number; y: number }[] {
  const sorted = [...words].sort((a, b) => a.y0 - b.y0 || a.x0 - b.x0)
  const out: { t: string; x0: number; x1: number; y: number }[] = []
  for (let i = 0; i < sorted.length; i++) {
    let w = sorted[i]
    if (w.x0 < minX) continue
    let t = w.t
    const x0 = w.x0
    if (
      isSign(t) &&
      sorted[i + 1] &&
      Math.abs(sorted[i + 1].y0 - w.y0) < 15 &&
      sorted[i + 1].x0 - w.x1 < 40
    ) {
      t += sorted[i + 1].t
      w = sorted[i + 1]
      i++
    }
    if (NUMTOK.test(t)) out.push({ t, x0, x1: w.x1, y: (w.y0 + w.y1) / 2 })
  }
  return out
}
const NH_JUNK = /상세|메뉴|주문|잔고|이체|지수|확인|스크린|캡처|감지|본화면|체결일/

type NhCol = 'profit' | 'price' | 'qty' | 'cost' | 'settle' | undefined
const nhColKind = (top: string): NhCol =>
  /실현|손익/.test(top)
    ? 'profit'
    : /단가/.test(top)
      ? 'price'
      : /수량/.test(top)
        ? 'qty'
        : /비용/.test(top)
          ? 'cost'
          : /정산|금액/.test(top)
            ? 'settle'
            : undefined

/** undefined 값 키 제거(레코드 deepEqual/JSON 직렬화 일관성) */
const clean = (r: SaleRecord): SaleRecord =>
  Object.fromEntries(Object.entries(r).filter(([, v]) => v !== undefined)) as SaleRecord

function parseNhList(lines: Line[], words: Word[], opt: Opts): SaleRecord[] {
  const W = opt.width,
    H = opt.height,
    leftMax = 0.285 * W
  const hdrLines = lines.filter(
    (l) =>
      l.y > 0.28 * H &&
      l.y < 0.45 * H &&
      /종목명|수량|비용|정산|실현손익|수익률|단가/.test(l.compact),
  )
  if (!hdrLines.length) return []
  const hdrWords = hdrLines.flatMap((l) => l.words).filter((w) => w.x0 >= leftMax - 10)
  const hdrBottom = Math.max(...hdrLines.flatMap((l) => l.words.map((w) => w.y1)))
  const hdrMid = hdrWords.reduce((s, w) => s + (w.y0 + w.y1) / 2, 0) / Math.max(1, hdrWords.length)
  // 행 앵커: 왼쪽 열의 종목명(상세 버튼·헤더 위로 잘린 행은 이름이 없어 제외)
  const anchors: { name: string; y: number; yBtn?: number }[] = []
  for (const l of lines) {
    const left = l.words.filter((w) => w.x1 <= leftMax + 10 && w.y0 > hdrBottom)
    if (!left.length || l.y > 0.88 * H) continue
    const text = left.map((w) => w.t).join('')
    if (/^상세|^상$/.test(text.replace(/\s/g, ''))) {
      const a = anchors[anchors.length - 1]
      if (a && a.yBtn === undefined) a.yBtn = l.y
      continue
    }
    if (hangul(text) && !NH_JUNK.test(text)) anchors.push({ name: cleanName(text), y: l.y })
  }
  const toks = numTokens(words, leftMax).filter((k) => k.y > hdrBottom)
  // 열 = 숫자 우측 끝(x1) 군집. 헤더 단어는 첫 우측 끝 >= 단어 중심인 열에 속함.
  const edges: number[] = []
  for (const x of toks.map((k) => k.x1).sort((p, q) => p - q)) {
    if (!edges.length || x - edges[edges.length - 1] > 0.05 * W) edges.push(x)
    else edges[edges.length - 1] = x
  }
  const colIdx = (x: number) => {
    const i = edges.findIndex((e) => x <= e + 0.02 * W)
    return i < 0 ? edges.length - 1 : i
  }
  const kinds: NhCol[] = edges.map((_, ci) =>
    nhColKind(
      hdrWords
        .filter((w) => colIdx((w.x0 + w.x1) / 2) === ci && (w.y0 + w.y1) / 2 < hdrMid)
        .sort((a, b) => a.x0 - b.x0)
        .map((w) => w.t)
        .join(''),
    ),
  )
  return anchors.map((a, i) => {
    const lo = a.y - 0.03 * H,
      hi = Math.min(anchors[i + 1] ? anchors[i + 1].y - 0.03 * H : Infinity, a.y + 0.09 * H)
    const mid = a.yBtn ? (a.y + a.yBtn) / 2 : a.y + 0.0225 * H
    const rec: SaleRecord = { stockName: a.name }
    const top: Record<string, string> = {},
      bot: Record<string, string> = {}
    for (const k of toks.filter((k) => k.y >= lo && k.y < hi)) {
      const ci = colIdx(k.x1)
      const kd = kinds[ci]
      if (!kd) continue
      const m = k.y < mid ? top : bot
      m[kd] ??= k.t
    }
    const P = num(top.profit)
    if (P !== undefined) {
      rec.realizedProfit = P
      if (bot.profit) {
        const r = pct(bot.profit)
        if (r !== undefined) rec.profitRate = (P < 0 ? -1 : 1) * Math.abs(r)
      }
    }
    if (top.price) rec.salePrice = num(top.price)
    if (top.qty) rec.quantity = num(top.qty)
    if (top.cost) rec.costs = num(top.cost)
    if (top.settle) {
      const s = num(top.settle)
      rec.saleAmount = s !== undefined ? s + (rec.costs ?? 0) : undefined
    }
    return rec
  })
}

function parseNhDetail(lines: Line[], words: Word[], opt: Opts): SaleRecord[] {
  const W = opt.width,
    H = opt.height
  // 종목명: 제목 "종목별 매매내역" 아래 첫 한글 줄
  const ti = lines.findIndex((l) => /종목별매매내역|매매내역/.test(l.compact))
  let name = ''
  for (let i = ti + 1; i < lines.length && i <= ti + 3 && ti >= 0; i++)
    if (hangul(lines[i].text) && !/손익|매도|매수|제비용/.test(lines[i].compact)) {
      name = cleanName(lines[i].text)
      break
    }
  // 종목명을 못 읽어도(''), 병합 때 같은 실현손익의 기록에 붙는다
  if (/매매일|금액|수수료|매입|수량|세금|구분/.test(name)) name = ''
  // 요약: 손익금액 / 매도금액 / 제비용 / 매수금액
  const sum = lines.map((l) => l.compact).join('\n')
  const grab = (re: RegExp) => {
    const m = sum.match(re)
    return m ? num(m[1]) : undefined
  }
  const N = '([-−–△▼]?[\\dOo][\\dOo,.]*)'
  const sumProfit = grab(new RegExp(`손익금액${N}`)),
    sumCost = grab(new RegExp(`제비용${N}`))
  // 표 머리글: 첫 거래 날짜 줄 위, '매매일' 줄부터
  const dateLines = lines.filter((l) => /\d{4}\.\d{2}\.\d{2}/.test(l.text))
  if (!dateLines.length) return []
  const hdrTop = (lines.find((l) => /매매일/.test(l.compact)) ?? dateLines[0]).y - 0.02 * H
  const hdrWords = words.filter(
    (w) => w.y0 >= hdrTop && w.y1 <= dateLines[0].y - 0.015 * H && w.x0 > 0.2 * W,
  )
  const hdrMid = hdrWords.reduce((s, w) => s + (w.y0 + w.y1) / 2, 0) / Math.max(1, hdrWords.length)
  const byX = [...hdrWords].sort((a, b) => a.x0 + a.x1 - (b.x0 + b.x1))
  const cols: Word[][] = []
  for (const w of byX) {
    const c = cols[cols.length - 1]
    if (c && (w.x0 + w.x1) / 2 - Math.max(...c.map((v) => (v.x0 + v.x1) / 2)) < 0.07 * W) c.push(w)
    else cols.push([w])
  }
  type PCol = { cx: number; lo: number; hi: number; kind: string }
  const pcols: PCol[] = cols.map((c) => {
    const t = c
      .filter((w) => (w.y0 + w.y1) / 2 < hdrMid)
      .sort((a, b) => a.x0 - b.x0)
      .map((w) => w.t)
      .join('')
    const b = c
      .filter((w) => (w.y0 + w.y1) / 2 >= hdrMid)
      .sort((a, b2) => a.x0 - b2.x0)
      .map((w) => w.t)
      .join('')
    const kind = /실현|손익/.test(t + b)
      ? 'profit'
      : /수수료|세금/.test(t + b) && !/매입/.test(t)
        ? 'fee'
        : /매입|입금/.test(t)
          ? 'buy'
          : /금액|수량/.test(t + b)
            ? 'amount'
            : 'x'
    return {
      cx: c.reduce((s, w) => s + (w.x0 + w.x1) / 2, 0) / c.length,
      lo: Math.min(...c.map((w) => w.x0)),
      hi: Math.max(...c.map((w) => w.x1)),
      kind,
    }
  })
  const amts = pcols.filter((c) => c.kind === 'amount') // 금액 열이 둘이면 오른쪽이 매입금액('매입' 글자 누락)
  if (amts.length > 1) amts[amts.length - 1].kind = 'buy'
  // 표가 팝업 오른쪽 끝에서 잘린 열(숫자 일부만 보임, 예: 145가 '1')은 버린다. ponytail: x0>0.89W 고정 임계값, 레이아웃이 바뀌면 조정.
  const toks = numTokens(words, 0.2 * W).filter(
    (k) => k.y > dateLines[0].y - 0.02 * H && k.x0 < 0.89 * W,
  )
  const colOfTok = (k: { x0: number; x1: number }) =>
    pcols.find((c) => k.x1 >= c.lo - 0.02 * W && k.x1 <= c.hi + 0.04 * W) ??
    pcols.reduce((b, c) => (Math.abs(c.cx - k.x1) < Math.abs(b.cx - k.x1) ? c : b), pcols[0])
  const out: SaleRecord[] = []
  dateLines.forEach((dl, i) => {
    const dm = dl.text.match(/(\d{4})\s*\.\s*(\d{2})\s*\.\s*(\d{2})/)!
    const gl = lines.find(
      (l) =>
        l.y > dl.y + 0.01 * H && l.y < dl.y + 0.07 * H && /매수|매도|현금|금매/.test(l.compact),
    )
    const gy = gl ? gl.y : dl.y + 0.036 * H,
      mid = (dl.y + gy) / 2
    const end = dateLines[i + 1] ? dateLines[i + 1].y - 0.015 * H : gy + 0.03 * H
    if (!/매도/.test(gl?.compact ?? '')) return
    const rec: SaleRecord = { stockName: name, saleDate: iso(+dm[1], dm[2], dm[3]) }
    const top: Record<string, string> = {},
      bot: Record<string, string> = {}
    for (const k of toks.filter((k) => k.y > dl.y - 0.02 * H && k.y < end)) {
      const c = colOfTok(k)
      const m = k.y < mid ? top : bot
      m[c.kind] ??= k.t
    }
    rec.saleAmount = num(top.amount)
    rec.quantity = num(bot.amount)
    rec.fee = num(top.fee)
    rec.tax = num(bot.fee)
    rec.buyAmount = num(top.buy)
    rec.realizedProfit = num(top.profit)
    if (rec.realizedProfit !== undefined && bot.profit) {
      const r = pct(bot.profit)
      if (r !== undefined) rec.profitRate = (rec.realizedProfit < 0 ? -1 : 1) * Math.abs(r)
    }
    out.push(rec)
  })
  if (out.length === 1) {
    // 매도 1건이면 요약값으로 빈칸 보완
    const r = out[0]
    if (r.realizedProfit === undefined && sumProfit !== undefined) r.realizedProfit = sumProfit
    if (r.fee === undefined && r.tax === undefined && sumCost !== undefined) r.costs = sumCost
  }
  return out
}

/**
 * NH 검산: 매도금액 - 매입금액 - (수수료+제세금) = 실현손익 (음수 포함).
 * 팝업의 '매입금액'(매도분 취득원가)로 계산한다. 목록의 매수정산금액은 기간 매수분이라 쓰지 않는다.
 * 목록 단독 레코드는 손익률로 부호만 교차검증: |손익|/매입원가 = 손익률, 매입원가 = 정산금액 - 손익.
 * ponytail: 필드 자동 교정은 하지 않는다(KIS와 달리 입력이 부분적이라 해가 모호) — 불일치면 verified=false.
 */
function verifyNh(r: SaleRecord): SaleRecord {
  const o = { ...r }
  const cost =
    (o.fee ?? 0) + (o.tax ?? 0) + (o.fee === undefined && o.tax === undefined ? (o.costs ?? 0) : 0)
  // 부호 교차검증(목록): 정산금액 S = saleAmount - costs
  if (
    o.realizedProfit !== undefined &&
    o.profitRate !== undefined &&
    o.saleAmount !== undefined &&
    o.costs !== undefined &&
    o.buyAmount === undefined
  ) {
    const S = o.saleAmount - o.costs,
      P = Math.abs(o.realizedProfit),
      R = Math.abs(o.profitRate)
    const ok = (s: number) => {
      const b = S - s * P
      return b > 0 && Math.abs((P / b) * 100 - R) <= Math.max(0.02, R * 0.001)
    }
    const pos = ok(1),
      neg = ok(-1)
    if (pos !== neg) {
      const s = pos ? 1 : -1
      if (Math.sign(o.realizedProfit) !== s) {
        o.realizedProfit = s * P
        o.corrected = [...(o.corrected ?? []), 'realizedProfit']
      }
      o.profitRate = s * R
    }
  }
  if (o.profitRate !== undefined && o.realizedProfit !== undefined)
    o.profitRate = (o.realizedProfit < 0 ? -1 : 1) * Math.abs(o.profitRate)
  if (o.saleAmount !== undefined && o.buyAmount !== undefined && o.realizedProfit !== undefined) {
    o.verified = o.saleAmount - o.buyAmount - cost === o.realizedProfit
    if (o.verified && o.quantity && o.buyAmount % o.quantity === 0)
      o.buyPrice = o.buyAmount / o.quantity // 취득원가 기준 단가(목록의 매수평균단가는 기간 평균이라 다를 수 있음)
  }
  return o
}
