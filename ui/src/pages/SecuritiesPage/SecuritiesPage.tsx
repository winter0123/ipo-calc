import { Link } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { SecuritiesManage } from '@/features/securities-manage/index.ts'
import styles, { StyleScope } from './SecuritiesPage.styles.ts'

export default function SecuritiesPage() {
  const { current, isLoading } = useCurrentInvestor()

  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1>증권사 관리</h1>
          {isLoading ? (
            <p role="status">불러오는 중...</p>
          ) : current ? (
            <>
              <p>
                {current.displayName} 투자자의 증권사 계좌입니다. 등록한 증권사가 위에 보입니다.
              </p>
              <SecuritiesManage investorId={current.id} />
            </>
          ) : (
            <p>
              먼저 <Link to="/investors/register">투자자를 추가</Link>해 주세요.
            </p>
          )}
          <div className={styles.brokerSyncAction}>
            <Link to="/broker-sync" className={styles.brokerSyncButton}>
              증권사 연동
            </Link>
          </div>
        </div>
      }
    </StyleScope>
  )
}
