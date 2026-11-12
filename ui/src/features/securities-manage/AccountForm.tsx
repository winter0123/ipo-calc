import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { useCreateAccount, useUpdateAccount, useDeleteAccount } from './api.ts'
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
// 생성: 계좌번호 optional(미입력 시 계좌번호 없이 등록).
// 수정: 기존 번호가 있으면 마스킹 안내 + 빈 새 번호 입력칸을 보여 준다(입력값이 있으면 교체, 비워두면 무변경).
//       마스킹 값은 절대 입력 기본값으로 쓰지 않는다. 번호를 교체/최초 입력할 때는 저장 전 확인 다이얼로그를 거친다.
// 수정 모드에는 저장·취소·삭제 버튼이 있다. 삭제는 확인 다이얼로그를 거치며 409(청약 이력) 시 서버 메시지를 보여 준다.
export default function AccountForm({ target, onDone, onCancel }: Props) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const create = useCreateAccount()
  const update = useUpdateAccount()
  const del = useDeleteAccount()
  const isEdit = target.kind === 'edit'
  const [numberConfirmOpen, setNumberConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<FormValues | null>(null)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const { isPending, error } = isEdit ? update : create
  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      accountNumber: '',
      accountName: isEdit ? target.accountName : '',
    },
  })

  const submitEdit = (v: FormValues) => {
    const name = v.accountName.trim()
    if (target.kind !== 'edit') return
    const body: { accountName: string; accountNumber?: string } = { accountName: name }
    if (v.accountNumber !== '') body.accountNumber = v.accountNumber
    update.mutate({ id: target.accountId, ...body }, { onSuccess: onDone })
  }

  const onSubmit = (v: FormValues) => {
    if (target.kind === 'edit') {
      // 번호 교체/최초 입력이 있으면 저장 전 확인 다이얼로그를 연다(확인 전에는 PATCH를 보내지 않는다).
      if (v.accountNumber !== '') {
        setPendingValues(v)
        setNumberConfirmOpen(true)
        return
      }
      submitEdit(v)
      return
    }
    const name = v.accountName.trim()
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

  const formError = toErrorMessage(error)
  const deleteError = toErrorMessage(del.error)
  const idp = `${target.kind}-${target.kind === 'edit' ? target.accountId : target.firmId}`
  // 수정 모드에서 기존 번호가 있으면 마스킹 안내를 보여 준다. 입력칸은 항상 빈 칸으로 제공한다.
  const hasExistingNumber = isEdit && target.accountNumber != null && target.accountNumber !== ''
  const busy = isPending || del.isPending

  return (
    <StyleScope>
      {
        <>
          <form
            className={styles.form}
            onSubmit={handleSubmit(onSubmit)}
            aria-label={`${target.firmName} 계좌 ${isEdit ? '수정' : '등록'}`}
            noValidate
          >
            <div className={styles.field}>
              <label htmlFor={`${idp}-number`}>계좌번호</label>
              {hasExistingNumber && (
                <span className={styles.fieldHint}>
                  현재 등록된 계좌번호는 {target.accountNumber}입니다. 비워두면 기존 번호가
                  유지됩니다.
                </span>
              )}
              <input
                id={`${idp}-number`}
                inputMode="numeric"
                autoComplete="off"
                placeholder={
                  isEdit
                    ? hasExistingNumber
                      ? '변경할 계좌번호를 입력해 주세요.'
                      : '계좌번호를 입력해 주세요.'
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
                disabled={busy}
              />
              {errors.accountNumber && (
                <span id={`${idp}-number-error`} className={styles.fieldError}>
                  {errors.accountNumber.message}
                </span>
              )}
            </div>
            <div className={styles.field}>
              <label htmlFor={`${idp}-name`}>계좌명 (선택)</label>
              <input
                id={`${idp}-name`}
                placeholder="예: 주력 계좌"
                {...register('accountName')}
                disabled={busy}
              />
            </div>
            {formError && (
              <div className={styles.formError} role="alert">
                {formError}
              </div>
            )}
            {deleteError && (
              <div className={styles.formError} role="alert">
                {deleteError}
              </div>
            )}
            <div className={styles.actions}>
              <Button type="submit" disabled={busy}>
                {isPending ? '저장 중...' : isEdit ? '저장' : '등록'}
              </Button>
              <Button variant="secondary" onClick={onCancel} disabled={busy}>
                취소
              </Button>
              {isEdit && target.kind === 'edit' && (
                <Button
                  variant="secondary"
                  onClick={() => setDeleteConfirmOpen(true)}
                  disabled={busy}
                >
                  삭제
                </Button>
              )}
            </div>
          </form>
          <ConfirmDialog
            isOpen={numberConfirmOpen}
            title="계좌번호를 변경할까요?"
            message="계좌번호가 바뀌어도 기존 청약·매도 이력은 같은 계좌에 남습니다. 증권사의 실제 계좌와 맞는지 확인해 주세요."
            confirmLabel="변경"
            onCancel={() => {
              // 확인을 취소해도 입력값은 그대로 둔다(다시 확인하려면 저장을 다시 누르면 된다).
              setNumberConfirmOpen(false)
            }}
            onConfirm={() => {
              setNumberConfirmOpen(false)
              const v = pendingValues ?? getValues()
              setPendingValues(null)
              submitEdit(v)
            }}
            pending={update.isPending}
          />
          <ConfirmDialog
            isOpen={deleteConfirmOpen}
            title="계좌를 삭제할까요?"
            message="청약 이력이 없는 계좌만 해제할 수 있습니다. 연결된 청약 이력이 있으면 개별 삭제할 수 없습니다."
            confirmLabel="삭제"
            onCancel={() => setDeleteConfirmOpen(false)}
            onConfirm={() => {
              if (target.kind !== 'edit') return
              del.mutate(target.accountId, {
                onSuccess: () => {
                  setDeleteConfirmOpen(false)
                  onDone()
                },
                // 실패(예: 409 청약 이력) 시에도 다이얼로그를 닫아 폼의 오류 메시지를 보여 준다.
                onError: () => setDeleteConfirmOpen(false),
              })
            }}
            pending={del.isPending}
          />
        </>
      }
    </StyleScope>
  )
}
