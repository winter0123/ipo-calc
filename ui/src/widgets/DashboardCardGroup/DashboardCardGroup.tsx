import styles from './DashboardCardGroup.module.css'
import DashboardCard from './DashboardCard.tsx'
import type { DashboardSummary } from '../../features/dashboard/index.ts'

interface DashboardCardGroupProps {
  summary: DashboardSummary
}

const CARD_CONFIG: Array<{
  label: string
  format: 'currency' | 'percent' | 'count'
  key: keyof DashboardSummary
}> = [
  { label: '총 투자금', format: 'currency', key: 'totalInvestmentAmount' },
  { label: '세전 수익', format: 'currency', key: 'totalPreTaxProfit' },
  { label: '세후 수익', format: 'currency', key: 'totalAfterTaxProfit' },
  { label: '수익률', format: 'percent', key: 'totalProfitRate' },
  { label: '참여 건수', format: 'count', key: 'participationCount' },
]

export default function DashboardCardGroup({ summary }: DashboardCardGroupProps) {
  return (
    <div className={styles.grid}>
      {CARD_CONFIG.map(({ label, format, key }) => (
        <DashboardCard key={label} label={label} value={summary[key]} format={format} />
      ))}
    </div>
  )
}
