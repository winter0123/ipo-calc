import { useForm } from 'react-hook-form'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import { useCreateAccount, useUpdateAccount } from './api.ts'
import styles from './SecuritiesManage.module.css'

interface FormValues {
  accountNumber: string
  accountName: string
  isDefault: boolean
}

const formatAccountNumber = (v: string) =>
  v
    .replace(/\D/g, '')
    .slice(0, 16)
    .replace(/(.{4})/g, '$1-')
    .replace(/-$/, '')

export type AccountFormTarget =
  | { kind: 'create'; investorId: number; firmId: number; firmName: string }
  | { kind: 'edit'; accountId: number; firmName: string; accountName: string }

interface Props {
  target: AccountFormTarget
  onDone: () => void
  onCancel: () => void
}

// 증권사 카드 안에 펼치는 등록·수정 폼. 수정은 계좌명만 바꾼다(계좌번호는 서버가 수정을 받지 않는다).
export default function AccountForm({ target, onDone, onCancel }: Props) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const create = useCreateAccount()
  const update = useUpdateAccount()
  const isEdit = target.kind === 'edit'
  const { isPending, error } = isEdit ? update : create
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      accountNumber: '',
      accountName: isEdit ? target.accountName : '',
      isDefault: false,
    },
  })

  const onSubmit = (v: FormValues) => {
    const name = v.accountName.trim()
    if (target.kind === 'edit') {
      update.mutate({ id: target.accountId, accountName: name }, { onSuccess: onDone })
    } else {
      create.mutate(
        {
          investorId: target.investorId,
          securitiesFirmId: target.firmId,
          accountNumber: v.accountNumber,
          accountName: name || undefined,
          isDefault: v.isDefault,
        },
        { onSuccess: onDone },
      )
    }
  }

  const formError = toErrorMessage(error)
  const idp = `${target.kind}-${target.kind === 'edit' ? target.accountId : target.firmId}`

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label={`${target.firmName} 계좌 ${isEdit ? '수정' : '등록'}`}
      noValidate
    >
      {!isEdit && (
        <div className={styles.field}>
          <label htmlFor={`${idp}-number`}>계좌번호</label>
          <input
            id={`${idp}-number`}
            inputMode="numeric"
            autoComplete="off"
            placeholder="예: 1234-5678-90"
            aria-invalid={!!errors.accountNumber}
            aria-describedby={errors.accountNumber ? `${idp}-number-error` : undefined}
            {...register('accountNumber', {
              validate: (v) => {
                const digits = v.replace(/\D/g, '').length
                if (digits === 0) return '계좌번호를 입력해 주세요.'
                return (digits >= 10 && digits <= 16) || '계좌번호는 10~16자리 숫자여야 합니다.'
              },
              onChange: (e) => {
                e.target.value = formatAccountNumber(e.target.value)
              },
            })}
            disabled={isPending}
          />
          {errors.accountNumber && (
            <span id={`${idp}-number-error`} className={styles.fieldError}>
              {errors.accountNumber.message}
            </span>
          )}
        </div>
      )}
      <div className={styles.field}>
        <label htmlFor={`${idp}-name`}>계좌명 (선택)</label>
        <input
          id={`${idp}-name`}
          placeholder="예: 주력 계좌"
          {...register('accountName')}
          disabled={isPending}
        />
      </div>
      {!isEdit && (
        <label className={styles.check}>
          <input type="checkbox" {...register('isDefault')} disabled={isPending} />
          기본 계좌로 설정
        </label>
      )}
      {formError && (
        <div className={styles.formError} role="alert">
          {formError}
        </div>
      )}
      <div className={styles.actions}>
        <Button type="submit" disabled={isPending}>
          {isPending ? '저장 중...' : isEdit ? '저장' : '등록'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={isPending}>
          취소
        </Button>
      </div>
    </form>
  )
}
