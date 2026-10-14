import styles from './SaleRegisterForm.module.css'
import CaptureImport from './CaptureImport.tsx'
import SubscriptionPicker from './SubscriptionPicker.tsx'
import SaleBasicFields from './SaleBasicFields.tsx'
import SaleProfitFields from './SaleProfitFields.tsx'
import { useSaleForm } from './useSaleForm.ts'

export default function SaleRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const f = useSaleForm()

  if (f.investorsLoading) {
    return <div className={styles.loading}>목록을 불러오는 중...</div>
  }

  return (
    <form className={styles.form} onSubmit={f.submit} aria-label="매도 등록 폼" noValidate>
      <p className={styles.hint}>
        투자자: <strong>{f.current?.displayName ?? '선택 안 됨'}</strong>
      </p>
      <CaptureImport f={f} />
      <SubscriptionPicker f={f} />
      <SaleBasicFields f={f} />
      <SaleProfitFields f={f} />

      {f.formErrorMessage && (
        <div className={styles.formError} role="alert">
          {f.formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={f.isPending} className={styles.submitButton}>
        {f.isPending ? '등록 중...' : '등록'}
      </button>
    </form>
  )
}
