import { Link } from 'react-router-dom'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import styles, { StyleScope } from './SaleRegisterForm.styles.ts'
import type { SaleForm } from './useSaleForm.ts'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

const accountLabel = (account: { securitiesFirmName: string | null; accountName: string | null }) =>
  `${formatSecuritiesFirmName(account.securitiesFirmName ?? '증권사')} · ${account.accountName ?? '계좌'}`

/**
 * 공모주(고정)·증권계좌·배정 연결 상태를 보여 준다.
 * 청약·배정은 고객이 고르지 않고, 서버가 (투자자, 공모주, 계좌)로 자동으로 연결한다.
 */
export default function SaleConnectionFields({ f }: { f: SaleForm }) {
  'use no memo'
  const {
    effectiveIpoId,
    ipoName,
    ipoNameLoading,
    connectionId,
    connectionLoading,
    allocationData,
    needsAllocatedQuantity,
    securitiesAccounts,
    accountsLoading,
    connectedAccountId,
    errors,
    isPending,
  } = f
  const allocation = allocationData?.allocation
  // 연결(청약)에 고정된 계좌의 표시용 이름을 찾는다
  const connectedAccount =
    connectedAccountId != null
      ? securitiesAccounts.find((account) => account.id === connectedAccountId)
      : undefined

  return (
    <StyleScope>
      {
        <>
          {effectiveIpoId != null && (
            <div className={styles.field}>
              <label htmlFor="ipoName">공모주</label>
              <input
                id="ipoName"
                value={
                  ipoName ?? (ipoNameLoading ? '불러오는 중...' : '이름을 불러오지 못했습니다')
                }
                readOnly
                aria-busy={ipoNameLoading}
              />
            </div>
          )}

          {f.needsAccountSelect ? (
            <div className={styles.field}>
              <label htmlFor="securitiesAccountId">증권계좌 *</label>
              <select
                id="securitiesAccountId"
                {...f.register('securitiesAccountId', {
                  required: '증권계좌를 선택해 주세요.',
                  valueAsNumber: true,
                })}
                disabled={isPending || accountsLoading}
              >
                <option value="">계좌 선택</option>
                {securitiesAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {accountLabel(account)}
                  </option>
                ))}
              </select>
              {errors.securitiesAccountId && (
                <span className={styles.error} aria-live="polite">
                  {String(errors.securitiesAccountId.message)}
                </span>
              )}
            </div>
          ) : (
            <div className={styles.field}>
              <span id="connectedAccountLabel" className={styles.labelLike}>
                증권계좌
              </span>
              <p aria-labelledby="connectedAccountLabel" className={styles.staticValue}>
                {connectedAccount
                  ? accountLabel(connectedAccount)
                  : '이 청약에 연결된 계좌로 등록됩니다'}
              </p>
            </div>
          )}

          {connectionLoading && (
            <div className={styles.loading}>청약·배정 연결을 확인하는 중...</div>
          )}

          {allocation && allocationData?.remainingQuantity != null && (
            <div className={styles.referenceSection}>
              <ReferenceQuantity quantity={allocationData.remainingQuantity} />
            </div>
          )}

          {needsAllocatedQuantity && !connectionLoading && (
            <>
              <div className={styles.field}>
                <label htmlFor="allocatedQuantity">
                  배정수량{needsAllocatedQuantity ? ' *' : ''}
                </label>
                <input
                  id="allocatedQuantity"
                  type="number"
                  inputMode="numeric"
                  aria-invalid={!!errors.allocatedQuantity}
                  aria-describedby={
                    errors.allocatedQuantity ? 'allocatedQuantity-error' : undefined
                  }
                  {...f.register('allocatedQuantity', {
                    valueAsNumber: true,
                    validate: (v: number | undefined) => {
                      if (!needsAllocatedQuantity) return true
                      if (v == null || Number.isNaN(v)) return '배정수량을 입력해 주세요.'
                      return Number.isInteger(v) && v > 0 ? true : '0 초과 정수를 입력해 주세요.'
                    },
                  })}
                  disabled={isPending}
                />
                {errors.allocatedQuantity && (
                  <span id="allocatedQuantity-error" className={styles.error} aria-live="polite">
                    {String(errors.allocatedQuantity.message)}
                  </span>
                )}
              </div>
              <p className={styles.hint}>
                {connectionId != null
                  ? '이 청약은 배정 입력 전입니다. 배정수량을 입력하면 등록할 때 배정이 함께 생성됩니다. '
                  : '이 조합으로 등록된 청약·배정이 없습니다. 배정수량을 입력하면 등록할 때 청약·배정이 함께 생성됩니다. '}
                <Link to={`/ipos/${effectiveIpoId}/manage`}>청약·매도 관리에서 배정 입력</Link>
              </p>
            </>
          )}
        </>
      }
    </StyleScope>
  )
}
