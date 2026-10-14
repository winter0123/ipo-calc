import { profitToneClass } from '@/shared/lib/profitTone.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { otherFeesFromRealizedProfit, DEFAULT_IPO_FEE } from '@/entities/sale/index.ts'
import CapturedBadge from './CapturedBadge.tsx'
import styles from './SaleRegisterForm.module.css'
import { isNum, won, type SaleForm } from './useSaleForm.ts'

const validateNonNegativeInteger = (v: unknown) =>
  !isNum(v) || (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'

/** 세금 및 기타 수수료·실현손익·공모주수수료와 최종수익금 요약. */
export default function SaleProfitFields({ f }: { f: SaleForm }) {
  'use no memo'
  const { errors, isPending, ipoPrice, finalAmount } = f
  return (
    <>
      <div className={styles.field}>
        <label htmlFor="otherFees">세금 및 기타 수수료 (선택)</label>
        <MoneyInput
          id="otherFees"
          control={f.control}
          name="otherFees"
          aria-invalid={!!errors.otherFees}
          aria-describedby={errors.otherFees ? 'otherFees-error' : 'otherFees-hint'}
          validate={validateNonNegativeInteger}
          onValueChange={f.onFeesChange}
          allowNegative
          disabled={isPending}
        />
        <CapturedBadge f={f} name="otherFees" />
        {errors.otherFees ? (
          <span id="otherFees-error" className={styles.error} aria-live="polite">
            {String(errors.otherFees.message)}
          </span>
        ) : (
          <span id="otherFees-hint" className={styles.hint}>
            비워 두고 아래 실현손익만 넣어도 됩니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="realizedProfit">실현손익</label>
        <MoneyInput
          id="realizedProfit"
          control={f.control}
          name="realizedProfit"
          aria-invalid={!!errors.realizedProfit}
          aria-describedby={errors.realizedProfit ? 'realizedProfit-error' : 'realizedProfit-hint'}
          requiredMessage="실현손익을 입력해 주세요."
          validate={(v) => {
            if (!isNum(v) || !Number.isInteger(v)) return '정수를 입력해 주세요.'
            if (f.lastEdited !== 'profit') return true
            const { price, quantity, otherFees } = f.getValues()
            if (ipoPrice == null) {
              return (
                isNum(otherFees) || '공모가를 알 수 없어 세금 및 기타 수수료를 직접 입력해 주세요.'
              )
            }
            if (!isNum(price) || !isNum(quantity)) return true
            return (
              otherFeesFromRealizedProfit(price, quantity, ipoPrice, v) >= 0 ||
              '실현손익이 (매도단가×수량 − 공모가×수량)보다 클 수 없습니다.'
            )
          }}
          onValueChange={f.onProfitChange}
          allowNegative
          disabled={isPending}
        />
        <CapturedBadge f={f} name="realizedProfit" />
        {errors.realizedProfit ? (
          <span id="realizedProfit-error" className={styles.error} aria-live="polite">
            {String(errors.realizedProfit.message)}
          </span>
        ) : (
          <span id="realizedProfit-hint" className={styles.hint}>
            증권사 앱의 실현손익을 넣으세요.
            {f.grossValue !== undefined && ` (수수료 반영 전 차익 ${won(f.grossValue)})`}
            <details className={styles.help}>
              <summary>계산식 보기</summary>
              실현손익 = 매도단가×수량 − 공모가×수량 − 세금 및 기타 수수료. 공모주수수료는 빼지
              않습니다.
            </details>
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="ipoFee">공모주수수료</label>
        <MoneyInput
          id="ipoFee"
          control={f.control}
          name="ipoFee"
          aria-invalid={!!errors.ipoFee}
          aria-describedby={errors.ipoFee ? 'ipoFee-error' : 'ipoFee-hint'}
          validate={validateNonNegativeInteger}
          allowNegative
          disabled={isPending}
        />
        {errors.ipoFee ? (
          <span id="ipoFee-error" className={styles.error} aria-live="polite">
            {String(errors.ipoFee.message)}
          </span>
        ) : (
          <span id="ipoFee-hint" className={styles.hint}>
            {f.hasPriorSales
              ? '이미 매도 기록이 있어 0원으로 시작합니다(배정 1건당 한 번).'
              : `기본 ${won(DEFAULT_IPO_FEE)}입니다. 증권사별로 다르면 고치세요.`}
          </span>
        )}
      </div>

      <div className={styles.summary} role="group" aria-label="수익 요약">
        <span className={styles.summaryLabel}>최종수익금</span>
        <output
          htmlFor="realizedProfit ipoFee"
          aria-live="polite"
          className={`${styles.summaryValue} ${finalAmount === undefined ? '' : profitToneClass(finalAmount)}`}
        >
          {finalAmount === undefined ? '-' : won(finalAmount)}
        </output>
      </div>
    </>
  )
}
