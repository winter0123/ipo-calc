import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { PeriodGroup } from './groupProfits.ts'

// 큰 값은 만원 단위로 줄여 축 폭을 아낀다.
const axisWon = (v: number) =>
  Math.abs(v) >= 10000 ? `${Math.round(v / 10000).toLocaleString()}만` : v.toLocaleString()

export default function ProfitLineChart({ data }: { data: PeriodGroup[] }) {
  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} accessibilityLayer margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid stroke="var(--border)" vertical={false} />
        <XAxis
          dataKey="label"
          stroke="var(--ink-muted)"
          tick={{ fill: 'var(--ink-muted)', fontSize: 12 }}
        />
        <YAxis
          stroke="var(--ink-muted)"
          tick={{ fill: 'var(--ink-muted)', fontSize: 12 }}
          tickFormatter={axisWon}
          width={56}
        />
        <ReferenceLine y={0} stroke="var(--ink-muted)" strokeDasharray="4 4" />
        <Tooltip
          labelFormatter={(_, items) => {
            const p = items?.[0]?.payload as PeriodGroup | undefined
            return p ? `${p.title} · ${p.count}건` : ''
          }}
          formatter={(v) => [`${Number(v).toLocaleString()}원`, '최종수익금']}
        />
        <Line
          type="linear"
          dataKey="profit"
          name="최종수익금"
          stroke="var(--primary)"
          strokeWidth={2}
          dot={{ r: 3, fill: 'var(--primary)' }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
