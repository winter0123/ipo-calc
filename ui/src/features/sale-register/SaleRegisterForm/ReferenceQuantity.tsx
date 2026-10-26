import styles, { StyleScope } from './ReferenceQuantity-sale-register.styles.ts'

interface ReferenceQuantityProps {
  quantity: number
}

/** 아직 팔지 않은 수량을 알려 주는 글(입력칸 아님). */
export default function ReferenceQuantity({ quantity }: ReferenceQuantityProps) {
  return (
    <StyleScope>
      {
        <p className={styles.container}>
          <span>남은 수량</span> <strong>{quantity.toLocaleString('ko-KR')}주</strong>
        </p>
      }
    </StyleScope>
  )
}
