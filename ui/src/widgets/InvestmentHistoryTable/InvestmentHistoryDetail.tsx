import { type InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { RecordActions } from '@/features/investment-edit/index.ts'
import { Link } from 'react-router-dom'
import { statusLabel } from './statusLabel.ts'
import styles from './InvestmentHistoryDetail.module.css'

function formatWon(value: number | null): string {
  return value === null ? '-' : `${value.toLocaleString()}원`
}

interface InvestmentHistoryDetailProps {
  detail: InvestmentHistoryItem
  onClose: () => void
}

export default function InvestmentHistoryDetail({ detail, onClose }: InvestmentHistoryDetailProps) {
  const formattedDate = new Date(detail.subscribedAt).toLocaleDateString('ko-KR')

  const nextAction =
    detail.allocation === null
      ? { label: '배정 입력', to: `/allocations/register?subscriptionId=${detail.id}` }
      : (detail.remainingQuantity ?? 0) > 0
        ? { label: '매도 등록', to: `/sales/register?subscriptionId=${detail.id}` }
        : null

  return (
    <div className={styles.detail}>
      <header className={styles.header}>
        <h2 className={styles.title}>청약 상세</h2>
        <button
          type="button"
          className={styles.closeButton}
          onClick={onClose}
          aria-label="상세 정보 닫기"
        >
          ✕
        </button>
      </header>

      <dl className={styles.dl}>
        <div className={styles.dlRow}>
          <dt>청약일</dt>
          <dd>{formattedDate}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>공모주명</dt>
          <dd>{detail.ipoName}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>투자자명</dt>
          <dd>{detail.investorName ?? '-'}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>증권사명</dt>
          <dd>{detail.securitiesFirmName ?? '-'}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>공모가</dt>
          <dd>{formatWon(detail.ipoFixedPrice)}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>청약수량</dt>
          <dd>{detail.subscriptionQuantity}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>상태</dt>
          <dd>{statusLabel(detail.subscriptionStatus)}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>잔여수량</dt>
          <dd aria-readonly="true">{detail.remainingQuantity ?? '배정 전'}</dd>
        </div>
      </dl>

      <RecordActions
        kind="subscription"
        id={detail.id}
        label={`${detail.ipoName ?? '이'} 청약`}
        onDeleted={onClose}
      />

      {nextAction && (
        <Link to={nextAction.to} className={styles.actionLink}>
          {nextAction.label}
        </Link>
      )}

      {detail.allocation && (
        <div className={styles.nestedGroup}>
          <h3 className={styles.groupTitle}>배정 정보</h3>
          <dl className={styles.dl}>
            <div className={styles.dlRow}>
              <dt>배정 수량</dt>
              <dd>{detail.allocation.allocatedQuantity}</dd>
            </div>
            <div className={styles.dlRow}>
              <dt>배정 가격</dt>
              <dd>
                {detail.allocation.allocationPrice !== null
                  ? detail.allocation.allocationPrice.toLocaleString() + '원'
                  : '-'}
              </dd>
            </div>
            <div className={styles.dlRow}>
              <dt>배정 상태</dt>
              <dd>{statusLabel(detail.allocation.allocationStatus)}</dd>
            </div>
          </dl>
          <RecordActions
            kind="allocation"
            id={detail.allocation.id}
            label={`배정 ${detail.allocation.allocatedQuantity}주`}
          />
        </div>
      )}

      {detail.sales.length > 0 && (
        <div className={styles.nestedGroup}>
          <h3 className={styles.groupTitle}>매도 내역</h3>
          <table className={styles.salesTable}>
            <thead>
              <tr>
                <th scope="col">수량</th>
                <th scope="col">금액</th>
                <th scope="col">공모주수수료</th>
                <th scope="col">세금 및 기타</th>
                <th scope="col">상태</th>
                <th scope="col">매도일</th>
                <th scope="col">관리</th>
              </tr>
            </thead>
            <tbody>
              {detail.sales.map((sale) => (
                <tr key={sale.id}>
                  <td>{sale.quantity}</td>
                  <td>{formatWon(sale.amount)}</td>
                  <td>{formatWon(sale.fee)}</td>
                  <td>{formatWon(sale.tax)}</td>
                  <td>{statusLabel(sale.status)}</td>
                  <td>{new Date(sale.soldAt).toLocaleDateString('ko-KR')}</td>
                  <td>
                    <RecordActions kind="sale" id={sale.id} label={`매도 ${sale.quantity}주`} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
