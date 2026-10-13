import { Link, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { AccountSelector } from '@/features/investment-history/index.ts'
import { BrokerConnectPanel } from '@/features/broker-connect/index.ts'
import { BrokerSyncPanel } from '@/features/broker-sync/index.ts'
import styles from './BrokerSyncPage.module.css'

export default function BrokerSyncPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  // 투자자는 상단 전환기의 현재 투자자다.
  const { current } = useCurrentInvestor()
  const investorId = current ? String(current.id) : ''
  const securitiesAccountId = searchParams.get('securitiesAccountId') ?? ''
  const selectedAccountId = Number(securitiesAccountId)

  const handleAccountChange = (value: string) =>
    setSearchParams(value ? { securitiesAccountId: value } : {}, { replace: true })

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>증권사 연동</h1>
      <AccountSelector
        investorId={investorId}
        securitiesAccountId={securitiesAccountId}
        onSecuritiesAccountChange={handleAccountChange}
      />
      {Number.isInteger(selectedAccountId) && selectedAccountId > 0 ? (
        <>
          <BrokerConnectPanel
            key={`connect-${selectedAccountId}`}
            securitiesAccountId={selectedAccountId}
          />
          <BrokerSyncPanel
            key={`sync-${selectedAccountId}`}
            securitiesAccountId={selectedAccountId}
          />
        </>
      ) : (
        <p>
          증권계좌를 선택해 주세요. 계좌가 없다면 <Link to="/securities">증권사 관리</Link>에서 먼저
          등록하세요.
        </p>
      )}
    </div>
  )
}
