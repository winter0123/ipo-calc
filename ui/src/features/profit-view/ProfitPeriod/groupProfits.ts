export type PeriodUnit = 'year' | 'month' | 'day'

export interface PeriodGroup {
  key: string
  /** 그래프 x축 라벨 (연 `2026년`, 월 `2026. 10.`, 일 `10. 15.`) */
  label: string
  /** 표·툴팁용 전체 라벨 */
  title: string
  count: number
  saleAmount: number
  profit: number
}

interface GroupableSale {
  saleDate: string
  listingDate: string | null
  saleAmount: number | null
  afterTaxProfit: number
}

const KEY_LENGTH: Record<PeriodUnit, number> = { year: 4, month: 7, day: 10 }

function labelsOf(unit: PeriodUnit, key: string) {
  const [y, m, d] = key.split('-').map(Number)
  if (unit === 'year') return { label: `${y}년`, title: `${y}년` }
  if (unit === 'month') return { label: `${y}. ${m}.`, title: `${y}. ${m}.` }
  return { label: `${m}. ${d}.`, title: `${y}. ${m}. ${d}.` }
}

// 다음 달/해의 키. 연·월 빈 구간을 채울 때만 쓴다.
function nextKey(unit: PeriodUnit, key: string): string {
  const [y, m] = key.split('-').map(Number)
  if (unit === 'year') return String(y + 1)
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`
}

/**
 * 매도 목록을 기준일(공모주 상장일, 없으면 매도일)로 묶어 기간별 합계를 낸다. 오래된 기간부터.
 * 연·월은 from~to(빈 값이면 데이터의 처음~끝) 사이의 빈 기간을 0으로 채우고, 일은 데이터가 있는 날만 낸다.
 */
export function groupProfits(
  sales: GroupableSale[],
  unit: PeriodUnit,
  from = '',
  to = '',
): PeriodGroup[] {
  const len = KEY_LENGTH[unit]
  const groups = new Map<string, PeriodGroup>()
  const add = (key: string): PeriodGroup => {
    let g = groups.get(key)
    if (!g) {
      g = { key, ...labelsOf(unit, key), count: 0, saleAmount: 0, profit: 0 }
      groups.set(key, g)
    }
    return g
  }

  for (const sale of sales) {
    const g = add((sale.listingDate ?? sale.saleDate).slice(0, len))
    g.count += 1
    g.saleAmount += sale.saleAmount ?? 0
    g.profit += sale.afterTaxProfit
  }

  if (unit !== 'day' && groups.size > 0) {
    const keys = [...groups.keys()].sort()
    const last = (to ? to.slice(0, len) : '') || keys[keys.length - 1]
    for (let k = from ? from.slice(0, len) : keys[0]; k <= last; k = nextKey(unit, k)) add(k)
  }

  return [...groups.values()].sort((a, b) => a.key.localeCompare(b.key))
}
