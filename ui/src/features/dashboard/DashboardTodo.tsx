import { Link } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useAllInvestmentHistory } from '@/entities/subscription/index.ts'
import styles from './DashboardHome.module.css'
import { formatShares } from '@/shared/lib/format.ts'

export default function DashboardTodo() {
  const { current, investors, isLoading: investorLoading } = useCurrentInvestor()
  const { data, isLoading, error } = useAllInvestmentHistory(
    current ? { investorId: current.id } : {},
    { enabled: !!current },
  )
  const items = data ?? []
  const needAllocation = items.filter((i) => i.allocation === null)
  const needSale = items.filter((i) => i.allocation !== null && (i.remainingQuantity ?? 0) > 0)

  const renderBody = () => {
    if (investorLoading || (current && isLoading))
      return <p className={styles.muted}>불러오는 중...</p>
    if (!current && investors.length === 0)
      return (
        <p className={styles.muted}>
          먼저 <Link to="/investors/register">투자자를 등록</Link>해 주세요.
        </p>
      )
    if (!current)
      return <p className={styles.muted}>상단에서 투자자를 선택하면 할 일을 보여 드립니다.</p>
    if (error) {
      return (
        <p className={styles.error} role="alert">
          할 일을 불러오는 데 실패했습니다.
        </p>
      )
    }
    if (needAllocation.length === 0 && needSale.length === 0) {
      return <p className={styles.muted}>처리할 일이 없습니다.</p>
    }
    return (
      <ul className={styles.list}>
        {needAllocation.map((i) => (
          <li key={`a-${i.id}`} className={styles.row}>
            <span>
              {i.ipoName ?? '공모주'} · 배정 결과를 입력하세요 (
              {new Date(i.subscribedAt).toLocaleDateString('ko-KR')} 청약)
            </span>
            <Link to={`/allocations/register?subscriptionId=${i.id}`}>배정 입력</Link>
          </li>
        ))}
        {needSale.map((i) => (
          <li key={`s-${i.id}`} className={styles.row}>
            <span>
              {i.ipoName ?? '공모주'} · 아직 안 판 수량 {formatShares(i.remainingQuantity ?? 0)}
            </span>
            <Link to={`/sales/register?subscriptionId=${i.id}`}>매도 등록</Link>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="dash-todo" className={styles.card}>
      <h2 id="dash-todo" className={styles.title}>
        할 일
      </h2>
      {renderBody()}
    </section>
  )
}
