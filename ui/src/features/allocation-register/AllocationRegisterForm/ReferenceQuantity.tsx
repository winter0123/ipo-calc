import styles from './ReferenceQuantity.module.css'
import { formatShares } from '@/shared/lib/format.ts'

interface ReferenceQuantityProps {
  ipoName: string | null
  subscribedAt: string
  quantity: number
}

/** 배정을 입력할 청약을 공모주 이름·청약일·청약수량으로 알려 준다(읽기 전용 글, 입력칸 아님). */
export default function ReferenceQuantity({
  ipoName,
  subscribedAt,
  quantity,
}: ReferenceQuantityProps) {
  const [y, m, d] = subscribedAt.slice(0, 10).split('-').map(Number)
  return (
    <p className={styles.container}>
      <strong>{ipoName ?? '공모주'}</strong>
      <span>
        청약일 {new Date(y, m - 1, d).toLocaleDateString('ko-KR')} · 청약수량{' '}
        {formatShares(quantity)}
      </span>
    </p>
  )
}
