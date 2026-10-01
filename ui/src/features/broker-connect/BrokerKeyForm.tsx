import { useForm } from 'react-hook-form'
import Button from '@/shared/components/Button/Button.tsx'
import styles from './BrokerConnectPanel.module.css'

export interface KeyFormValues {
  appKey: string
  appSecret: string
  keyIssuedOn: string
}

interface Props {
  idPrefix: string
  submitLabel: string
  pendingLabel: string
  pending: boolean
  errorMessage: string | null
  today: string
  onSubmit: (values: KeyFormValues) => void
}

// 앱키·앱시크릿은 화면에 다시 보여주지 않는 password 입력이고, 제출 뒤 입력값을 비운다(부모가 key로 다시 마운트).
export default function BrokerKeyForm({
  idPrefix,
  submitLabel,
  pendingLabel,
  pending,
  errorMessage,
  today,
  onSubmit,
}: Props) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const {
    register: field,
    handleSubmit,
    formState: { errors },
  } = useForm<KeyFormValues>({ mode: 'onSubmit', defaultValues: { keyIssuedOn: today } })

  return (
    <form onSubmit={handleSubmit(onSubmit)} className={styles.form} noValidate>
      <div className={styles.field}>
        <label htmlFor={`${idPrefix}-app-key`}>앱키</label>
        <input
          id={`${idPrefix}-app-key`}
          type="password"
          autoComplete="off"
          maxLength={512}
          aria-invalid={errors.appKey ? 'true' : undefined}
          aria-describedby={errors.appKey ? `${idPrefix}-app-key-error` : undefined}
          {...field('appKey', { required: '앱키를 입력해 주세요.' })}
        />
        {errors.appKey && (
          <span id={`${idPrefix}-app-key-error`} role="alert" className={styles.error}>
            {errors.appKey.message}
          </span>
        )}
      </div>
      <div className={styles.field}>
        <label htmlFor={`${idPrefix}-app-secret`}>앱시크릿</label>
        <input
          id={`${idPrefix}-app-secret`}
          type="password"
          autoComplete="off"
          maxLength={512}
          aria-invalid={errors.appSecret ? 'true' : undefined}
          aria-describedby={errors.appSecret ? `${idPrefix}-app-secret-error` : undefined}
          {...field('appSecret', { required: '앱시크릿을 입력해 주세요.' })}
        />
        {errors.appSecret && (
          <span id={`${idPrefix}-app-secret-error`} role="alert" className={styles.error}>
            {errors.appSecret.message}
          </span>
        )}
      </div>
      <div className={styles.field}>
        <label htmlFor={`${idPrefix}-issued-on`}>키 발급일(증권사 서비스 신청일)</label>
        <input
          id={`${idPrefix}-issued-on`}
          type="date"
          max={today}
          aria-invalid={errors.keyIssuedOn ? 'true' : undefined}
          aria-describedby={errors.keyIssuedOn ? `${idPrefix}-issued-on-error` : undefined}
          {...field('keyIssuedOn', {
            validate: (v) => !v || v <= today || '발급일은 오늘 이후일 수 없습니다.',
          })}
        />
        {errors.keyIssuedOn && (
          <span id={`${idPrefix}-issued-on-error`} role="alert" className={styles.error}>
            {errors.keyIssuedOn.message}
          </span>
        )}
      </div>
      {errorMessage && (
        <p role="alert" className={styles.error}>
          {errorMessage}
        </p>
      )}
      <Button type="submit" disabled={pending}>
        {pending ? pendingLabel : submitLabel}
      </Button>
    </form>
  )
}
