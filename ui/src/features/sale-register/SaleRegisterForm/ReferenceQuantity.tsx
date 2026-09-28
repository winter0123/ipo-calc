import styles from './ReferenceQuantity.module.css'

interface ReferenceQuantityProps {
  quantity: number | null
}

export default function ReferenceQuantity({ quantity }: ReferenceQuantityProps) {
  return (
    <div className={styles.container}>
      <label htmlFor="referenceQuantity" className={styles.label}>
        남은 수량 (참고)
      </label>
      <input
        id="referenceQuantity"
        type="number"
        value={quantity ?? ''}
        readOnly
        disabled
        aria-readonly="true"
        className={styles.input}
      />
      <span className={styles.unit}>주</span>
    </div>
  )
}
