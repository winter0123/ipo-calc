import type { InvestmentHistoryItem } from '../../entities/subscription/types.ts'
import styles from './InvestmentHistoryDetail.module.css'

interface InvestmentHistoryDetailProps {
  detail: InvestmentHistoryItem
  onClose: () => void
}

export default function InvestmentHistoryDetail({ detail, onClose }: InvestmentHistoryDetailProps) {
  const formattedDate = new Date(detail.subscribedAt).toLocaleDateString('ko-KR')

  return (
    <div className={styles.detail}>
      <button
        type="button"
        className={styles.closeButton}
        onClick={onClose}
        aria-label="상세 정보 닫기"
      >
        ✕
      </button>

      <dl className={styles.dl}>
        <div className={styles.dlRow}>
          <dt>구독 ID</dt>
          <dd>{detail.id}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>구독일</dt>
          <dd>{formattedDate}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>IPO ID</dt>
          <dd>{detail.ipoId}</dd>
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
          <dt>IPO 고정가</dt>
          <dd>{detail.ipoFixedPrice.toLocaleString()}원</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>청약수량</dt>
          <dd>{detail.subscriptionQuantity}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>상태</dt>
          <dd>{detail.subscriptionStatus}</dd>
        </div>
        <div className={styles.dlRow}>
          <dt>잔여수량</dt>
          <dd aria-readonly="true">{detail.remainingQuantity ?? '-'}</dd>
        </div>
      </dl>

      {detail.allocation && (
        <div className={styles.nestedGroup}>
          <h3 className={styles.groupTitle}>배정 정보</h3>
          <dl className={styles.dl}>
            <div className={styles.dlRow}>
              <dt>배정 ID</dt>
              <dd>{detail.allocation.id}</dd>
            </div>
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
              <dd>{detail.allocation.allocationStatus}</dd>
            </div>
          </dl>
        </div>
      )}

      {detail.sales.length > 0 && (
        <div className={styles.nestedGroup}>
          <h3 className={styles.groupTitle}>매도 내역</h3>
          <table className={styles.salesTable}>
            <thead>
              <tr>
                <th scope="col">매도 ID</th>
                <th scope="col">수량</th>
                <th scope="col">금액</th>
                <th scope="col">수수료</th>
                <th scope="col">세금</th>
                <th scope="col">기타비용</th>
                <th scope="col">상태</th>
                <th scope="col">매도일</th>
              </tr>
            </thead>
            <tbody>
              {detail.sales.map((sale) => (
                <tr key={sale.id}>
                  <td>{sale.id}</td>
                  <td>{sale.quantity}</td>
                  <td>{sale.amount.toLocaleString()}원</td>
                  <td>{sale.fee.toLocaleString()}원</td>
                  <td>{sale.tax.toLocaleString()}원</td>
                  <td>{sale.otherCost.toLocaleString()}원</td>
                  <td>{sale.status}</td>
                  <td>{new Date(sale.soldAt).toLocaleDateString('ko-KR')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
