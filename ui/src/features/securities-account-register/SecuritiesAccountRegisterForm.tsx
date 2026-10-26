import { useForm, useWatch } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { useSecuritiesFirms } from '@/entities/securities-firm/index.ts'
import {
  useRegisterSecuritiesAccount,
  type RegisterSecuritiesAccountRequest,
  type RegisterSecuritiesAccountResponse,
} from './useRegisterSecuritiesAccount.ts'
import styles, { StyleScope } from './SecuritiesAccountRegisterForm.styles.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function SecuritiesAccountRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const navigate = useNavigate()
  const params = useParams<{ investorId: string }>()
  const { data: firms, isLoading: firmsLoading, error: firmsError } = useSecuritiesFirms()
  const { mutate, isPending, error } = useRegisterSecuritiesAccount()

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    control,
  } = useForm<RegisterSecuritiesAccountRequest>({ mode: 'onSubmit' })

  const investorId = params.investorId ? Number(params.investorId) : 0

  const accountNumberInput = useWatch({ control, name: 'accountNumber' })
  const formattedAccountNumber = accountNumberInput
    ?.replace(/\D/g, '')
    .replace(/(.{4})/g, '$1-')
    .replace(/-$/, '')

  const onSubmit = (data: RegisterSecuritiesAccountRequest) => {
    mutate(
      { ...data, investorId },
      {
        onSuccess: (response: RegisterSecuritiesAccountResponse) => {
          alert(`등록 완료: ${response.accountNumber}`)
          navigate('/dashboard')
        },
      },
    )
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: {
      400: (e) =>
        e.serverMessage?.includes('존재하지 않는 투자자')
          ? '투자자 정보를 찾을 수 없습니다.'
          : undefined,
      404: (e) =>
        e.serverMessage?.includes('증권사') && !e.serverMessage.includes('투자자')
          ? '선택한 증권사를 찾을 수 없습니다.'
          : '투자자 정보를 찾을 수 없습니다.',
    },
  })

  if (firmsLoading) {
    return (
      <StyleScope>{<div className={styles.loading}>증권사 목록을 불러오는 중...</div>}</StyleScope>
    )
  }

  if (firmsError) {
    return (
      <StyleScope>
        {
          <div className={styles.error}>
            증권사 목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
          </div>
        }
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {
        <form
          className={styles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label="증권계좌 등록 폼"
          noValidate
        >
          <div className={styles.field}>
            <label htmlFor="securitiesFirmId">증권사</label>
            <select
              id="securitiesFirmId"
              aria-invalid={!!errors.securitiesFirmId}
              aria-describedby={errors.securitiesFirmId ? 'securitiesFirmId-error' : undefined}
              {...register('securitiesFirmId', {
                required: '증권사를 선택해 주세요.',
                valueAsNumber: true,
              })}
              disabled={isPending}
            >
              <option value="">증권사 선택</option>
              {firms?.map((firm) => (
                <option key={firm.id} value={firm.id}>
                  {firm.name}
                </option>
              ))}
            </select>
            {errors.securitiesFirmId && (
              <span id="securitiesFirmId-error" className={styles.error} aria-live="polite">
                {String(errors.securitiesFirmId.message)}
              </span>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="accountNumber">계좌번호</label>
            <input
              id="accountNumber"
              type="text"
              placeholder="예: 1234-5678-90"
              aria-invalid={!!errors.accountNumber}
              aria-describedby={errors.accountNumber ? 'accountNumber-error' : undefined}
              {...register('accountNumber', {
                required: '계좌번호를 입력해 주세요.',
                validate: (v) => {
                  const digits = v?.replace(/\D/g, '') ?? ''
                  return (
                    (digits.length >= 10 && digits.length <= 16) ||
                    '계좌번호는 10~16자리 숫자여야 합니다.'
                  )
                },
              })}
              value={formattedAccountNumber ?? ''}
              onChange={(e) => {
                const digits = e.target.value.replace(/\D/g, '').slice(0, 16)
                const formatted = digits.replace(/(.{4})/g, '$1-').replace(/-$/, '')
                setValue('accountNumber', formatted, { shouldValidate: true })
              }}
              disabled={isPending}
            />
            {errors.accountNumber && (
              <span id="accountNumber-error" className={styles.error} aria-live="polite">
                {String(errors.accountNumber.message)}
              </span>
            )}
            <p className={styles.hint}>
              숫자만 입력하면 자동으로 하이픈이 삽입됩니다. 한국투자증권 연동에는
              10자리(8자리+2자리)가 필요하며, 키움·LS증권은 계좌번호 없이 앱키로 조회합니다.
            </p>
          </div>

          <div className={styles.field}>
            <label htmlFor="accountName">계좌명 (선택)</label>
            <input
              id="accountName"
              type="text"
              placeholder="예: 주력 계좌"
              aria-invalid={!!errors.accountName}
              aria-describedby={errors.accountName ? 'accountName-error' : undefined}
              {...register('accountName')}
              disabled={isPending}
            />
            {errors.accountName && (
              <span id="accountName-error" className={styles.error} aria-live="polite">
                {String(errors.accountName.message)}
              </span>
            )}
          </div>

          <div className={styles.field}>
            <label className={styles.checkboxLabel}>
              <input
                type="checkbox"
                aria-invalid={!!errors.isDefault}
                aria-describedby={errors.isDefault ? 'isDefault-error' : undefined}
                {...register('isDefault')}
                disabled={isPending}
              />
              기본 계좌로 설정
            </label>
            {errors.isDefault && (
              <span id="isDefault-error" className={styles.error} aria-live="polite">
                {String(errors.isDefault.message)}
              </span>
            )}
          </div>

          {formErrorMessage && (
            <div className={styles.formError} role="alert">
              {formErrorMessage}
            </div>
          )}

          <button type="submit" disabled={isPending} className={styles.submitButton}>
            {isPending ? '등록 중...' : '등록'}
          </button>
        </form>
      }
    </StyleScope>
  )
}
