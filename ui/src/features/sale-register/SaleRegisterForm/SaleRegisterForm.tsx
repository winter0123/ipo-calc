import { useNavigate } from 'react-router-dom'
import styles, { StyleScope } from './SaleRegisterForm.styles.ts'
import CaptureImport from './CaptureImport.tsx'
import SubscriptionPicker from './SubscriptionPicker.tsx'
import SaleBasicFields from './SaleBasicFields.tsx'
import SaleProfitFields from './SaleProfitFields.tsx'
import { useSaleForm } from './useSaleForm.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

export default function SaleRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const f = useSaleForm()
  const navigate = useNavigate()

  if (f.investorsLoading) {
    return <StyleScope>{<div className={styles.loading}>목록을 불러오는 중...</div>}</StyleScope>
  }

  const isDirectMode = !f.selectedSubscriptionId && f.selectedInvestorId

  if (f.missingUnderwriterAccount) {
    return (
      <ConfirmDialog
        isOpen
        title="주관사 계좌를 등록해 주세요"
        message={
          <>
            {f.underwriterNames.map(formatSecuritiesFirmName).join(', ')} 중 한 곳의 증권계좌를
            마이페이지에서 등록해 주세요.
          </>
        }
        confirmLabel="마이페이지"
        cancelLabel="돌아가기"
        onConfirm={() => navigate('/my-page')}
        onCancel={() => navigate('/subscriptions')}
      />
    )
  }

  return (
    <StyleScope>
      {
        <form className={styles.form} onSubmit={f.submit} aria-label="매도 등록 폼" noValidate>
          <p className={styles.hint}>
            투자자: <strong>{f.current?.displayName ?? '선택 안 됨'}</strong>
          </p>

          {isDirectMode && (
            <div className={styles.modeBadge}>
              <span className={styles.modeIcon}>⚡</span>
              <span>직접 매도 등록 모드</span>
              <span className={styles.modeDesc}>
                청약·배정 없이 바로 매도 기록을 남깁니다. 배정수량과 증권계좌는 필수입니다.
              </span>
            </div>
          )}

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
      }
    </StyleScope>
  )
}
