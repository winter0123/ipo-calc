import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'
import { useLogin } from './useLogin.ts'
import { isValidEmail, isMinLength } from '@/shared/lib/validators.ts'
import type { LoginRequest } from './types.ts'
import { NETWORK_ERROR_MESSAGE, toErrorMessage } from '@/shared/api/apiError.ts'
import styles from './LoginForm.module.css'

// 외부 사이트로의 open redirect를 막기 위해 앱 내부 경로만 허용한다.
function returnPathFrom(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')
    ? from
    : '/dashboard'
}

export default function LoginForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const navigate = useNavigate()
  const location = useLocation()
  const { mutate, isPending, error } = useLogin()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: LoginRequest) => {
    mutate(data, {
      onSuccess: () => {
        navigate(returnPathFrom(location.state))
      },
    })
  }

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="로그인 폼"
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
            validate: (v) => isMinLength(v, 8) || '비밀번호는 최소 8자 이상이어야 합니다.',
          })}
        />
        {errors.password && (
          <span id="password-error" className={styles.error} aria-live="polite">
            {String(errors.password.message)}
          </span>
        )}
      </div>
      {error && (
        <div className={styles.formError} role="alert">
          {toErrorMessage(error, {
            byStatus: { 401: '이메일 또는 비밀번호가 올바르지 않습니다.' },
            fallback: NETWORK_ERROR_MESSAGE,
          })}
        </div>
      )}
      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '로그인 중...' : '로그인'}
      </button>
    </form>
  )
}
