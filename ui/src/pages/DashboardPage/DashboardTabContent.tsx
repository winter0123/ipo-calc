import { EmptyState } from '@/widgets/EmptyState/index.ts'
import { DashboardCardGroup } from '@/widgets/DashboardCardGroup/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type {
  DashboardByInvestor,
  DashboardByPeriod,
  DashboardBySecuritiesFirm,
  DashboardSummary,
  DashboardTab,
} from '@/features/dashboard/index.ts'
import { formatWon } from '@/shared/lib/format.ts'
import styles, { StyleScope } from './DashboardTabContent.styles.ts'

const EMPTY_SUMMARY: DashboardSummary = {
  totalInvestmentAmount: 0,
  totalPreTaxProfit: 0,
  totalAfterTaxProfit: 0,
  totalProfitRate: 0,
  participationCount: 0,
}

interface DashboardTabContentProps {
  currentTab: DashboardTab
  summary?: DashboardSummary
  investorSummaries?: DashboardByInvestor[]
  investors: InvestorResponse[]
  securitiesFirmSummaries?: DashboardBySecuritiesFirm[]
  periodSummaries?: DashboardByPeriod[]
  onResetFilters: () => void
}

export default function DashboardTabContent({
  currentTab,
  summary,
  investorSummaries,
  investors,
  securitiesFirmSummaries,
  periodSummaries,
  onResetFilters,
}: DashboardTabContentProps) {
  switch (currentTab) {
    case 'all': {
      if (!summary) return null
      if (summary.totalInvestmentAmount === 0 && summary.participationCount === 0) {
        return <EmptyState onResetFilters={onResetFilters} />
      }
      // 매도가 없는 투자자는 집계에 없으므로 0으로 채워 모든 투자자를 보여 준다.
      const byId = new Map((investorSummaries ?? []).map((item) => [item.investorId, item]))
      const sections = investors.map((investor) => ({
        id: investor.id,
        name: investor.displayName,
        hasSales: byId.has(investor.id),
        summary: byId.get(investor.id) ?? EMPTY_SUMMARY,
      }))
      return (
        <StyleScope>
          {
            <div className={styles.dataArea}>
              <section aria-labelledby="dash-total" className={styles.section}>
                <h2 id="dash-total" className={styles.sectionTitle}>
                  종합
                </h2>
                <DashboardCardGroup summary={summary} />
              </section>
              {sections.length > 0 && (
                // 투자자가 많으면 길어지므로 4명 이상일 때는 접힌 채로 시작한다.
                <details className={styles.investors} open={sections.length <= 3}>
                  <summary className={styles.investorsSummary}>
                    투자자별 ({sections.length}명)
                  </summary>
                  {sections.map((section) => (
                    <section
                      key={section.id}
                      aria-labelledby={`dash-investor-${section.id}`}
                      className={styles.section}
                    >
                      <h2 id={`dash-investor-${section.id}`} className={styles.sectionTitle}>
                        {section.name}
                      </h2>
                      {section.hasSales ? (
                        <DashboardCardGroup summary={section.summary} />
                      ) : (
                        <p className={styles.noSales}>아직 매도 기록이 없습니다.</p>
                      )}
                    </section>
                  ))}
                </details>
              )}
            </div>
          }
        </StyleScope>
      )
    }
    case 'securities-firm': {
      if (!securitiesFirmSummaries) return null
      if (securitiesFirmSummaries.length === 0) {
        return <EmptyState onResetFilters={onResetFilters} />
      }
      return (
        <StyleScope>
          {
            <div className={styles.dataArea}>
              <AmountList
                items={securitiesFirmSummaries.map((item) => ({
                  key: item.securitiesFirmName,
                  label: item.securitiesFirmName,
                  amount: item.totalInvestmentAmount,
                }))}
              />
            </div>
          }
        </StyleScope>
      )
    }
    case 'month':
    case 'year': {
      if (!periodSummaries) return null
      if (periodSummaries.length === 0) return <EmptyState onResetFilters={onResetFilters} />
      return (
        <StyleScope>
          {
            <div className={styles.dataArea}>
              <AmountList
                items={periodSummaries.map((item) => ({
                  key: item.period,
                  label: item.period,
                  amount: item.totalInvestmentAmount,
                }))}
              />
            </div>
          }
        </StyleScope>
      )
    }
  }
}

interface AmountListItem {
  key: string
  label: string
  amount: number
}

function AmountList({ items }: { items: AmountListItem[] }) {
  return (
    <StyleScope>
      {
        <ul className={styles.list}>
          {items.map((item) => (
            <li key={item.key}>
              {item.label}: {formatWon(item.amount)}
            </li>
          ))}
        </ul>
      }
    </StyleScope>
  )
}
