import styles from './DashboardCardGroup.module.css'
import DashboardCard from './DashboardCard.tsx'
import type { DashboardSummary } from '@/features/dashboard/index.ts'

interface DashboardCardGroupProps {
  summary: DashboardSummary
}

const CARD_CONFIG: Array<{
  label: string
  format: 'currency' | 'percent' | 'count'
  key: keyof DashboardSummary
  signed?: boolean
}> = [
  { label: '총 투자금', format: 'currency', key: 'totalInvestmentAmount' },
  { label: '최종수익금', format: 'currency', key: 'totalAfterTaxProfit', signed: true },
  { label: '수익률', format: 'percent', key: 'totalProfitRate', signed: true },
  { label: '참여 건수', format: 'count', key: 'participationCount' },
]

export default function DashboardCardGroup({ summary }: DashboardCardGroupProps) {
  return (
    <div className={styles.grid}>
      {CARD_CONFIG.map(({ label, format, key, signed }) => (
        <DashboardCard
          key={label}
          label={label}
          value={summary[key]}
          format={format}
          signed={signed}
        />
      ))}
    </div>
  )
}
