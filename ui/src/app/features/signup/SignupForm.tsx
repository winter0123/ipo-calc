import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useSignup } from './useSignup.ts'
import { isValidEmail, isMinLength } from '../../shared/lib/validators.ts'
import type { SignupRequest } from './useSignup.ts'
import styles from './SignupForm.module.css'

function hasTwoCategories(v: string): boolean {
  const hasLetter = /[a-zA-Z]/.test(v)
  const hasDigit = /\d/.test(v)
  const hasSpecial = /[^a-zA-Z0-9]/.test(v)
  return [hasLetter, hasDigit, hasSpecial].filter(Boolean).length >= 2
}

export default function SignupForm() {
  const navigate = useNavigate()
  const { mutate, isPending, error } = useSignup()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: SignupRequest) => {
    mutate(data, {
      onSuccess: (res) => {
        navigate('/investors/register', {
          state: { accountId: (res as { accountId?: number }).accountId },
        })
      },
      onError: () => {
        // 에러 메시지는 error 상태로 처리
      },
    })
  }

  const getFormErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('409') || msg.includes('Conflict')) {
      return '이미 사용 중인 이메일입니다.'
    }
    if (msg.includes('400') || msg.includes('Request failed')) {
      return msg.includes('Request failed') ? '입력값을 확인해 주세요.' : msg
    }
    if (msg) return msg
    return '네트워크 연결을 확인해 주세요.'
  }

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="회원가입 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="email">이메일</label>
        <input
          id="email"
          type="email"
          aria-invalid={!!errors.email || !!error}
          aria-describedby="email-error"
          {...register('email', {
            required: '필수 입력 항목입니다.',
            validate: (v) => isValidEmail(v) || '올바른 이메일 형식을 입력해 주세요.',
          })}
        />
        {errors.email && (
          <span id="email-error" className={styles.error} aria-live="polite">
            {String(errors.email.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="password">비밀번호</label>
        <input
          id="password"
          type="password"
          aria-invalid={!!errors.password || !!error}
          aria-describedby="password-error"
          {...register('password', {
            required: '필수 입력 항목입니다.',
            validate: (v) =>
              (v.length >= 8 && v.length <= 72 && hasTwoCategories(v)) ||
              '비밀번호는 최소 8자 이상이어야 합니다.',
          })}
        />
        {errors.password && (
          <span id="password-error" className={styles.error} aria-live="polite">
            {String(errors.password.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="name">이름</label>
        <input
          id="name"
          type="text"
          aria-invalid={!!errors.name || !!error}
          aria-describedby="name-error"
          {...register('name', {
            required: '필수 입력 항목입니다.',
            validate: (v) =>
              (isMinLength(v, 2) && v.length <= 50) || '이름은 2자 이상 50자 이하여야 합니다.',
          })}
        />
        {errors.name && (
          <span id="name-error" className={styles.error} aria-live="polite">
            {String(errors.name.message)}
          </span>
        )}
      </div>

      {getFormErrorMessage() && (
        <div className={styles.formError} aria-live="polite">
          {getFormErrorMessage()}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '가입 중...' : '가입하기'}
      </button>
    </form>
  )
}
