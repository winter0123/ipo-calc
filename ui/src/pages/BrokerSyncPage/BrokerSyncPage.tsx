import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
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
  const accounts = useSecuritiesAccounts(current?.id)
  // URL의 계좌가 현재 투자자의 계좌 목록에 있을 때만 패널을 연다(투자자 전환 후 이전 계좌가 남지 않게)
  const loaded = accounts.data !== undefined && !accounts.isFetching
  const belongs = accounts.data?.some((a) => String(a.id) === securitiesAccountId) ?? false
  const selectedAccountId = belongs ? Number(securitiesAccountId) : 0
  const stale = securitiesAccountId !== '' && loaded && !belongs

  useEffect(() => {
    if (stale) setSearchParams({}, { replace: true })
  }, [stale, setSearchParams])

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
