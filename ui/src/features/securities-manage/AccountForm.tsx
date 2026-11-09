import { useForm } from 'react-hook-form'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import { useCreateAccount, useUpdateAccount } from './api.ts'
import styles, { StyleScope } from './SecuritiesManage.styles.ts'

interface FormValues {
  accountNumber: string
  accountName: string
}

const formatAccountNumber = (v: string) =>
  v
    .replace(/\D/g, '')
    .slice(0, 16)
    .replace(/(.{4})/g, '$1-')
    .replace(/-$/, '')

export type AccountFormTarget =
  | { kind: 'create'; investorId: number; firmId: number; firmName: string }
  | {
      kind: 'edit'
      accountId: number
      firmName: string
      accountName: string
      accountNumber?: string | null
    }

interface Props {
  target: AccountFormTarget
  onDone: () => void
  onCancel: () => void
}

// 증권사 카드 안에 펼치는 등록·수정 폼.
// 생성: 계좌번호 optional(미입력 시 계좌번호 없이 등록). 수정: 번호가 없으면 입력칸으로 사후 입력,
//       번호가 있으면 마스킹 표시 + 교체 불가 안내(번호 입력칸 미제공).
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
    },
  })

  const onSubmit = (v: FormValues) => {
    const name = v.accountName.trim()
    if (target.kind === 'edit') {
      const body: { accountName: string; accountNumber?: string } = { accountName: name }
      if (v.accountNumber !== '') body.accountNumber = v.accountNumber
      update.mutate({ id: target.accountId, ...body }, { onSuccess: onDone })
    } else {
      create.mutate(
        {
          investorId: target.investorId,
          securitiesFirmId: target.firmId,
          accountNumber: v.accountNumber || undefined,
          accountName: name || undefined,
        },
        { onSuccess: onDone },
      )
    }
  }

  const formError = toErrorMessage(error)
  const idp = `${target.kind}-${target.kind === 'edit' ? target.accountId : target.firmId}`
  // 수정 모드에서 기존 번호가 있으면 입력칸을 내지 않고 마스킹+안내만 보여 준다(번호 교체 UI 금지).
  const hasExistingNumber = isEdit && target.accountNumber != null && target.accountNumber !== ''
  const showNumberInput = !isEdit || !hasExistingNumber

  return (
    <StyleScope>
      {
        <form
          className={styles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label={`${target.firmName} 계좌 ${isEdit ? '수정' : '등록'}`}
          noValidate
        >
          {showNumberInput && (
            <div className={styles.field}>
              <label htmlFor={`${idp}-number`}>계좌번호</label>
              <input
                id={`${idp}-number`}
                inputMode="numeric"
                autoComplete="off"
                placeholder={
                  isEdit && target.accountNumber == null
                    ? '계좌번호를 입력해 주세요.'
                    : '계좌번호(선택, 미입력 시 미입력)'
                }
                aria-invalid={!!errors.accountNumber}
                aria-describedby={errors.accountNumber ? `${idp}-number-error` : undefined}
                {...register('accountNumber', {
                  validate: (v) => {
                    const digits = v.replace(/\D/g, '').length
                    if (digits === 0) return undefined // 번호 미입력도 허용(선택 입력)
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
          {hasExistingNumber && (
            <div className={styles.fieldHintBlock}>
              <span className={styles.number}>{target.accountNumber}</span>
              <span className={styles.fieldHint}>이미 등록된 계좌번호입니다.</span>
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
      }
    </StyleScope>
  )
}
