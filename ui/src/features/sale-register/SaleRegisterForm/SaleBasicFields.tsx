import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import styles from './SaleRegisterForm.module.css'
import { won, type SaleForm } from './useSaleForm.ts'

/** 매도수량·매도단가·매도일. */
export default function SaleBasicFields({ f }: { f: SaleForm }) {
  'use no memo'
  const { errors, isPending, ipoPrice } = f
  return (
    <>
      <div className={styles.field}>
        <label htmlFor="quantity">매도수량</label>
        <input
          id="quantity"
          type="number"
          inputMode="numeric"
          aria-invalid={!!errors.quantity}
          aria-describedby={errors.quantity ? 'quantity-error' : 'quantity-hint'}
          {...f.register('quantity', {
            required: '매도수량을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
            onChange: () => f.syncLinked(),
          })}
          disabled={isPending}
        />
        {errors.quantity ? (
          <span id="quantity-error" className={styles.error} aria-live="polite">
            {String(errors.quantity.message)}
          </span>
        ) : (
          <span id="quantity-hint" className={styles.hint}>
            남은 수량이 먼저 들어갑니다. 실제 판 수량으로 고치세요.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="price">매도단가</label>
        <MoneyInput
          id="price"
          control={f.control}
          name="price"
          aria-invalid={!!errors.price}
          aria-describedby={errors.price ? 'price-error' : 'price-hint'}
          requiredMessage="매도단가를 입력해 주세요."
          validate={(v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.'}
          onValueChange={() => f.syncLinked()}
          allowNegative // 음수를 입력하면 지우지 않고 검증 메시지로 알린다
          disabled={isPending}
        />
        {errors.price ? (
          <span id="price-error" className={styles.error} aria-live="polite">
            {String(errors.price.message)}
          </span>
        ) : (
          <span id="price-hint" className={styles.hint}>
            {ipoPrice != null
              ? `공모가 ${won(ipoPrice)}이 먼저 들어갑니다.`
              : '공모가를 알 수 없어 비어 있습니다.'}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="saleDate">매도일</label>
        <input
          id="saleDate"
          type="date"
          aria-invalid={!!errors.saleDate}
          aria-describedby={errors.saleDate ? 'saleDate-error' : 'saleDate-hint'}
          {...f.register('saleDate', {
            required: '매도일을 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.saleDate ? (
          <span id="saleDate-error" className={styles.error} aria-live="polite">
            {String(errors.saleDate.message)}
          </span>
        ) : (
          <span id="saleDate-hint" className={styles.hint}>
            상장일이 먼저 들어갑니다.
          </span>
        )}
      </div>
    </>
  )
}
