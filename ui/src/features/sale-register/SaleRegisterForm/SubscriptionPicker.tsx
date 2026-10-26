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
  const { selectedSubscriptionId: subId, allocationData, errors } = f
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
              disabled={f.isPending || !f.selectedInvestorId || f.subscriptionsLoading}
            >
              <option value="">청약 선택</option>
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

          {/* 청약당 배정은 하나뿐이라 고르게 하지 않고 자동으로 연결한다. */}
          <input
            type="hidden"
            {...f.register('allocationId', { required: true, valueAsNumber: true })}
          />
          {subId && allocationData && !allocationData.allocation && (
            <p className={styles.error} role="alert">
              이 청약은 아직 배정 입력 전입니다. 배정을 먼저 등록해 주세요.{' '}
              <Link to={`/allocations/register?subscriptionId=${subId}`}>배정 등록하기</Link>
            </p>
          )}
          {errors.allocationId && !(subId && allocationData?.allocation) && (
            <p className={styles.error} role="alert">
              매도할 청약을 선택해 주세요.
            </p>
          )}
        </>
      }
    </StyleScope>
  )
}
