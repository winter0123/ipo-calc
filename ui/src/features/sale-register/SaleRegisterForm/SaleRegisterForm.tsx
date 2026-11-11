import { Link, useNavigate } from 'react-router-dom'
import styles, { StyleScope } from './SaleRegisterForm.styles.ts'
import CaptureImport from './CaptureImport.tsx'
import SaleConnectionFields from './SaleConnectionFields.tsx'
import SaleBasicFields from './SaleBasicFields.tsx'
import SaleProfitFields from './SaleProfitFields.tsx'
import { useSaleForm } from './useSaleForm.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

const PROBLEM_MESSAGE = {
  mismatch: '이 링크의 공모주와 청약 정보가 서로 다릅니다. 공모주 청약 목록에서 다시 열어 주세요.',
  'subscription-not-found': '이 청약을 찾을 수 없거나 내 계정의 청약이 아닙니다.',
  'ipo-not-found': '공모주를 찾을 수 없습니다.',
} as const

// 필수 조회 중/오류를 명시적 안내와 함께 보여 주고 등록을 막는다
const BLOCK_MESSAGE = {
  'ipo-loading': '공모주 정보를 확인하는 중입니다…',
  'insight-loading': '공모주 주관사 정보를 확인하는 중입니다…',
  'insight-failed': '공모주 주관사 정보를 확인하지 못했습니다.',
  'accounts-loading': '증권계좌 목록을 확인하는 중입니다…',
  'accounts-failed': '증권계좌 목록을 불러오지 못했습니다.',
  'lookup-loading': '청약·배정 연결을 확인하는 중입니다…',
  'lookup-failed': '청약·배정 연결을 확인하지 못했습니다.',
  'detail-loading': '연결된 청약·배정 정보를 확인하는 중입니다…',
  'account-required': '증권계좌를 선택해 주세요.',
  'account-not-allowed':
    '선택한 계좌는 등록에 사용할 수 없습니다. 공모주 주관사에 해당하는 계좌를 선택해 주세요.',
} as const

const FAILED_REASONS = new Set(['insight-failed', 'accounts-failed', 'lookup-failed'])

export default function SaleRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const f = useSaleForm()
  const navigate = useNavigate()

  if (f.investorsLoading) {
    return <StyleScope>{<div className={styles.loading}>목록을 불러오는 중...</div>}</StyleScope>
  }

  // 문맥(공모주·청약)이 없는 /sales/register — 임의 선택 없이 청약 목록으로 안내만 한다
  if (f.guide) {
    return (
      <StyleScope>
        <div className={styles.guide}>
          <h2>매도 등록은 공모주를 고른 뒤 시작합니다</h2>
          <p>
            공모주 청약 목록에서 공모주를 누르고 그 화면의 <strong>매도 등록</strong>을 열어 주세요.
            청약·배정은 공모주와 증권계좌만으로 자동으로 연결됩니다.
          </p>
          <Link to="/subscriptions">공모주 청약 목록으로 이동</Link>
        </div>
      </StyleScope>
    )
  }

  // 링크가 가리키는 공모주·청약이 없거나 남의 것이면 등록하지 않는다
  if (f.entryProblem) {
    return (
      <StyleScope>
        <div className={styles.blockError} role="alert">
          {PROBLEM_MESSAGE[f.entryProblem]}
        </div>
      </StyleScope>
    )
  }

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

          <CaptureImport f={f} />
          <SaleConnectionFields f={f} />
          <SaleBasicFields f={f} />
          <SaleProfitFields f={f} />

          {f.submitBlockReason && (
            <div
              className={FAILED_REASONS.has(f.submitBlockReason) ? styles.formError : styles.hint}
              role={FAILED_REASONS.has(f.submitBlockReason) ? 'alert' : 'status'}
            >
              <p>{BLOCK_MESSAGE[f.submitBlockReason]}</p>
              {FAILED_REASONS.has(f.submitBlockReason) && (
                <button
                  type="button"
                  className={styles.retryButton}
                  onClick={f.retryEssentialQueries}
                >
                  다시 시도
                </button>
              )}
            </div>
          )}

          {f.formErrorMessage && (
            <div className={styles.formError} role="alert">
              {f.formErrorMessage}
            </div>
          )}

          <button
            type="submit"
            disabled={f.isPending || f.submitBlocked}
            className={styles.submitButton}
          >
            {f.isPending ? '등록 중...' : '등록'}
          </button>
        </form>
      }
    </StyleScope>
  )
}
