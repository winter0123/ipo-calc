import {
  useSecuritiesAccounts,
  type SecuritiesAccount,
} from '@/entities/securities-account/index.ts'
import styles, { StyleScope } from './AccountSelector.styles.ts'

export interface AccountSelectorProps {
  investorId: string
  securitiesAccountId: string
  onSecuritiesAccountChange: (securitiesAccountId: string) => void
}

export default function AccountSelector({
  investorId,
  securitiesAccountId,
  onSecuritiesAccountChange,
}: AccountSelectorProps) {
  const { data: securitiesAccounts, isLoading: accountsLoading } = useSecuritiesAccounts(
    investorId ? Number(investorId) : undefined,
  )

  const handleSecuritiesAccountChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onSecuritiesAccountChange(e.target.value)
  }

  const getSecuritiesAccountDisplayName = (account: SecuritiesAccount) => {
    const firmName = account.securitiesFirmName ?? '알 수 없는 증권사'
    const accountNum = account.accountNumber
    return `${firmName} - ${accountNum}`
  }

  return (
    <StyleScope>
      {
        <div className={styles.container}>
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
          </div>
        </div>
      }
    </StyleScope>
  )
}
