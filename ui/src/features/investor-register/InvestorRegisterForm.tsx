import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useRegisterInvestor, type RegisterInvestorRequest } from './useRegisterInvestor.ts'
import { isValidDisplayName } from '../../shared/lib/validators.ts'
import styles from './InvestorRegisterForm.module.css'
import { toErrorMessage } from '../../shared/api/apiError.ts'

const RELATIONSHIP_OPTIONS = [
  { value: 'SELF', label: '본인' },
  { value: 'SPOUSE', label: '배우자' },
  { value: 'PARENT', label: '부모' },
  { value: 'CHILD', label: '자녀' },
  { value: 'OTHER', label: '기타' },
] as const

export default function InvestorRegisterForm() {
  const navigate = useNavigate()
  const { mutate, isPending, error } = useRegisterInvestor()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterInvestorRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: RegisterInvestorRequest) => {
    mutate(data, {
      onSuccess: () => {
        navigate('/dashboard')
      },
    })
  }

  const formErrorMessage = toErrorMessage(error)

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="투자자 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="displayName">표시 이름</label>
        <input
          id="displayName"
          type="text"
          aria-invalid={!!errors.displayName || !!error}
          aria-describedby="displayName-error"
          {...register('displayName', {
            required: '표시 이름은 필수입니다.',
            validate: (v) => isValidDisplayName(v) || '표시 이름은 50자 이하여야 합니다.',
          })}
        />
        {errors.displayName && (
          <span id="displayName-error" className={styles.error} aria-live="polite">
            {String(errors.displayName.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="relationship">관계</label>
        <select
          id="relationship"
          aria-invalid={!!errors.relationship || !!error}
          aria-describedby="relationship-error"
          {...register('relationship', {
            validate: (v) =>
              !v ||
              ['SELF', 'SPOUSE', 'PARENT', 'CHILD', 'OTHER'].includes(v) ||
              '유효한 관계를 선택해 주세요.',
          })}
        >
          <option value="">선택 안 함</option>
          {RELATIONSHIP_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {errors.relationship && (
          <span id="relationship-error" className={styles.error} aria-live="polite">
            {String(errors.relationship.message)}
          </span>
        )}
      </div>

      {formErrorMessage && (
        <div className={styles.formError} aria-live="polite">
          {formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '등록 중...' : '등록'}
      </button>
    </form>
  )
}
