import { useSearchParams } from 'react-router-dom'
import { AccountSelector } from '@/features/investment-history/index.ts'
import { BrokerConnectPanel } from '@/features/broker-connect/index.ts'
import { BrokerSyncPanel } from '@/features/broker-sync/index.ts'
import styles from './BrokerSyncPage.module.css'

export default function BrokerSyncPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const investorId = searchParams.get('investorId') ?? ''
  const securitiesAccountId = searchParams.get('securitiesAccountId') ?? ''
  const selectedAccountId = Number(securitiesAccountId)

  // 투자자가 바뀌면 이전 증권계좌 선택은 무효이므로 한 번에 같이 바꾼다.
  const handleInvestorChange = (value: string) =>
    setSearchParams(value ? { investorId: value } : {}, { replace: true })
  const handleAccountChange = (value: string) =>
    setSearchParams(value ? { investorId, securitiesAccountId: value } : { investorId }, {
      replace: true,
    })

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>증권사 연동</h1>
      <AccountSelector
        investorId={investorId}
        securitiesAccountId={securitiesAccountId}
        onInvestorChange={handleInvestorChange}
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
        <p>투자자와 증권계좌를 선택해 주세요.</p>
      )}
    </div>
  )
}
