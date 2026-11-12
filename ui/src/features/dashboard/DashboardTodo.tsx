import { Link } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useAllInvestmentHistory } from '@/entities/subscription/index.ts'
import styles, { StyleScope } from './DashboardHome.styles.ts'
import { formatShares } from '@/shared/lib/format.ts'

export default function DashboardTodo() {
  const { current, investors, isLoading: investorLoading } = useCurrentInvestor()
  const { data, isLoading, error } = useAllInvestmentHistory(
    current ? { investorId: current.id } : {},
    { enabled: !!current },
  )
  const items = data ?? []
  const needSale = items.filter((i) => i.allocation !== null && (i.remainingQuantity ?? 0) > 0)

  const renderBody = () => {
    if (investorLoading || (current && isLoading))
      return <StyleScope>{<p className={styles.muted}>불러오는 중...</p>}</StyleScope>
    if (!current && investors.length === 0)
      return (
        <StyleScope>
          {
            <p className={styles.muted}>
              먼저 <Link to="/investors/register">투자자를 등록</Link>해 주세요.
            </p>
          }
        </StyleScope>
      )
    if (!current)
      return (
        <StyleScope>
          {
            <p className={styles.muted}>
              상단에서 투자자를 선택하면 매도 미입력 항목을 보여 드립니다.
            </p>
          }
        </StyleScope>
      )
    if (error) {
      return (
        <StyleScope>
          {
            <p className={styles.error} role="alert">
              매도 현황을 불러오는 데 실패했습니다.
            </p>
          }
        </StyleScope>
      )
    }
    if (needSale.length === 0) {
      return <StyleScope>{<p className={styles.muted}>미입력된 매도가 없습니다.</p>}</StyleScope>
    }
    return (
      <StyleScope>
        {
          <ul className={styles.list}>
            {needSale.map((i) => (
              <li key={`s-${i.id}`} className={styles.row}>
                <span>
                  {i.ipoName ?? '공모주'} · 아직 안 판 수량 {formatShares(i.remainingQuantity ?? 0)}
                </span>
                <Link
                  to={
                    i.securitiesAccountId != null
                      ? `/ipos/${i.ipoId}/manage?securitiesAccountId=${i.securitiesAccountId}`
                      : `/ipos/${i.ipoId}/manage`
                  }
                  className={styles.saleRowLink}
                >
                  청약·매도 관리
                </Link>
              </li>
            ))}
          </ul>
        }
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {
        <section aria-labelledby="dash-todo" className={styles.card}>
          <h2 id="dash-todo" className={styles.title}>
            매도 미입력
          </h2>
          {renderBody()}
        </section>
      }
    </StyleScope>
  )
}
