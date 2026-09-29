import { useInvestorList } from '../useInvestorList.ts'
import { useSecuritiesAccountList } from '../useSecuritiesAccountList.ts'
import type { InvestorResponse } from '../../../entities/investor/types.ts'
import type { SecuritiesAccount } from '../../../entities/securities-account/types.ts'
import styles from './AccountSelector.module.css'

export interface AccountSelectorProps {
  investorId: string
  securitiesAccountId: string
  onInvestorChange: (investorId: string) => void
  onSecuritiesAccountChange: (securitiesAccountId: string) => void
}

export default function AccountSelector({
  investorId,
  securitiesAccountId,
  onInvestorChange,
  onSecuritiesAccountChange,
}: AccountSelectorProps) {
  const { data: investors, isLoading: investorsLoading, error: investorsError } = useInvestorList()
  const { data: securitiesAccounts, isLoading: accountsLoading } = useSecuritiesAccountList(
    investorId ? Number(investorId) : undefined,
  )

  const handleInvestorChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onInvestorChange(e.target.value)
  }

  const handleSecuritiesAccountChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onSecuritiesAccountChange(e.target.value)
  }

  const getInvestorDisplayName = (investor: InvestorResponse) => {
    return `${investor.displayName} (ID: ${investor.id})`
  }

  const getSecuritiesAccountDisplayName = (account: SecuritiesAccount) => {
    const firmName = account.securitiesFirmName ?? '알 수 없는 증권사'
    const accountNum = account.accountNumber
    return `${firmName} - ${accountNum}`
  }

  if (investorsLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.fieldGroup}>
          <label htmlFor="investorSelect" className={styles.label}>
            투자자
          </label>
          <select id="investorSelect" className={styles.select} disabled aria-busy="true">
            <option value="">투자자 로딩 중...</option>
          </select>
        </div>
        <div className={styles.fieldGroup}>
          <label htmlFor="securitiesAccountSelect" className={styles.label}>
            증권계좌
          </label>
          <select id="securitiesAccountSelect" className={styles.select} disabled aria-busy="true">
            <option value="">증권계좌 로딩 중...</option>
          </select>
        </div>
      </div>
    )
  }

  if (investorsError) {
    return (
      <div className={styles.container}>
        <div className={styles.fieldGroup}>
          <label htmlFor="investorSelect" className={styles.label}>
            투자자
          </label>
          <select id="investorSelect" className={styles.select} disabled aria-invalid="true">
            <option value="">투자자 목록을 불러올 수 없습니다</option>
          </select>
        </div>
      </div>
    )
  }

  const hasInvestors = investors && investors.length > 0

  return (
    <div className={styles.container}>
      <div className={styles.fieldGroup}>
        <label htmlFor="investorSelect" className={styles.label}>
          투자자
        </label>
        <select
          id="investorSelect"
          className={styles.select}
          value={investorId}
          onChange={handleInvestorChange}
          disabled={investorsLoading}
          aria-describedby={!hasInvestors ? 'investor-empty-hint' : undefined}
        >
          <option value="">전체</option>
          {hasInvestors &&
            investors!.map((investor) => (
              <option key={investor.id} value={String(investor.id)}>
                {getInvestorDisplayName(investor)}
              </option>
            ))}
        </select>
        {!hasInvestors && (
          <span id="investor-empty-hint" className={styles.hint}>
            등록된 투자자가 없습니다
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="securitiesAccountSelect" className={styles.label}>
          증권계좌
        </label>
        <select
          id="securitiesAccountSelect"
          className={styles.select}
          value={securitiesAccountId}
          onChange={handleSecuritiesAccountChange}
          disabled={!investorId || accountsLoading}
          aria-describedby={
            investorId && securitiesAccounts && securitiesAccounts.length === 0
              ? 'account-empty-hint'
              : undefined
          }
        >
          <option value="">전체</option>
          {investorId &&
            securitiesAccounts &&
            securitiesAccounts.length > 0 &&
            securitiesAccounts.map((account) => (
              <option key={account.id} value={String(account.id)}>
                {getSecuritiesAccountDisplayName(account)}
              </option>
            ))}
        </select>
        {investorId &&
          securitiesAccounts &&
          securitiesAccounts.length === 0 &&
          !accountsLoading && (
            <span id="account-empty-hint" className={styles.hint}>
              해당 투자자의 증권계좌가 없습니다
            </span>
          )}
        {!investorId && <span className={styles.hint}>투자자를 먼저 선택해 주세요</span>}
      </div>
    </div>
  )
}
