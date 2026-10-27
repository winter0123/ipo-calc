import { Link } from 'react-router-dom'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import styles, { StyleScope } from './SaleRegisterForm.styles.ts'
import type { SaleForm } from './useSaleForm.ts'

const formatDate = (value: string) => {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ko-KR')
}

/** 청약 선택, 남은 수량 참고, 배정 없음·미선택 안내. */
export default function SubscriptionPicker({ f }: { f: SaleForm }) {
  'use no memo'
  const {
    selectedSubscriptionId: subId,
    allocationData,
    errors,
    selectedInvestorId,
    securitiesAccounts,
    accountsLoading,
    isPending,
  } = f
  const hasNoSubscription = !subId && selectedInvestorId

  return (
    <StyleScope>
      {
        <>
          <div className={styles.field}>
            <label htmlFor="subscriptionId">청약</label>
            <select
              id="subscriptionId"
              value={subId ?? ''}
              onChange={f.handleSubscriptionChange}
              disabled={isPending || !selectedInvestorId || f.subscriptionsLoading}
            >
              <option value="">청약 선택 (직접 등록 시 비워두기)</option>
              {f.subscriptions.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.ipoName ?? '이름 없는 공모주'} · {formatDate(item.subscribedAt)}
                </option>
              ))}
            </select>
          </div>

          {subId && (
            <div className={styles.referenceSection}>
              {f.allocationLoading && <div className={styles.loading}>로딩 중...</div>}
              {allocationData && allocationData.remainingQuantity !== null && (
                <ReferenceQuantity quantity={allocationData.remainingQuantity} />
              )}
            </div>
          )}

          {/* 청약 선택 시: 배정 ID 자동 연결 */}
          {subId && (
            <input
              type="hidden"
              {...f.register('allocationId', { required: true, valueAsNumber: true })}
            />
          )}

          {/* 청약이 있고 배정이 없는 경우: 배정 등록 안내 */}
          {subId && allocationData && !allocationData.allocation && (
            <p className={styles.error} role="alert">
              이 청약은 아직 배정 입력 전입니다. 배정을 먼저 등록해 주세요.{' '}
              <Link to={`/allocations/register?subscriptionId=${subId}`}>배정 등록하기</Link>
            </p>
          )}

          {/* 청약을 선택하지 않고 직접 등록하는 경우: 필수 필드 표시 */}
          {hasNoSubscription && (
            <>
              <div className={styles.field}>
                <label htmlFor="allocatedQuantity">배정수량 *</label>
                <input
                  id="allocatedQuantity"
                  type="number"
                  inputMode="numeric"
                  aria-invalid={!!errors.allocatedQuantity}
                  aria-describedby={
                    errors.allocatedQuantity ? 'allocatedQuantity-error' : undefined
                  }
                  {...f.register('allocatedQuantity', {
                    required: '배정수량을 입력해 주세요.',
                    valueAsNumber: true,
                    validate: (v: number | undefined) =>
                      v != null && Number.isInteger(v) && v > 0
                        ? true
                        : '0 초과 정수를 입력해 주세요.',
                  })}
                  disabled={isPending}
                />
                {errors.allocatedQuantity && (
                  <span id="allocatedQuantity-error" className={styles.error} aria-live="polite">
                    {String(errors.allocatedQuantity.message)}
                  </span>
                )}
              </div>

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
                  {(securitiesAccounts ?? []).map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.securitiesFirmName ?? '증권사'} · {account.accountName ?? '계좌'}
                    </option>
                  ))}
                </select>
                {errors.securitiesAccountId && (
                  <span className={styles.error} aria-live="polite">
                    {String(errors.securitiesAccountId.message)}
                  </span>
                )}
              </div>
            </>
          )}

          {errors.allocationId && !(subId && allocationData?.allocation) && !hasNoSubscription && (
            <p className={styles.error} role="alert">
              매도할 청약을 선택하거나, 청약 없이 직접 등록하려면 배정수량과 증권계좌를 입력해
              주세요.
            </p>
          )}
        </>
      }
    </StyleScope>
  )
}
