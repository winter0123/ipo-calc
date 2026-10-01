import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useSignup } from './useSignup.ts'
import { isValidEmail, isMinLength } from '@/shared/lib/validators.ts'
import type { SignupRequest } from './useSignup.ts'
import styles from './SignupForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

function hasTwoCategories(v: string): boolean {
  const hasLetter = /[a-zA-Z]/.test(v)
  const hasDigit = /\d/.test(v)
  const hasSpecial = /[^a-zA-Z0-9]/.test(v)
  return [hasLetter, hasDigit, hasSpecial].filter(Boolean).length >= 2
}

export default function SignupForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
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
    })
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: { 409: '이미 사용 중인 이메일입니다.' },
  })

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
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
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
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'password-error' : undefined}
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
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'name-error' : undefined}
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

      {formErrorMessage && (
        <div className={styles.formError} role="alert">
          {formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '가입 중...' : '가입하기'}
      </button>
    </form>
  )
}
