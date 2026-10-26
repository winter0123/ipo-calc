import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import {
  getSubscription,
  investmentHistoryKeys,
  updateSubscription,
  type Subscription,
} from '@/entities/subscription/index.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { useRecordMutation } from './useRecordMutation.ts'
import { EditForm, Field, Shell } from './editDialogParts.tsx'
import { at9, ymd } from './editDialogHelpers.ts'

interface SubscriptionValues {
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo: string
}

function SubscriptionForm({ record, onClose }: { record: Subscription; onClose: () => void }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const save = useRecordMutation((data: SubscriptionValues) =>
    updateSubscription(record.id, { ...data, subscribedAt: at9(data.subscribedAt) }),
  )
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<SubscriptionValues>({
    defaultValues: {
      quantity: record.quantity,
      depositAmount: record.depositAmount,
      subscribedAt: ymd(record.subscribedAt),
      memo: record.memo ?? '',
    },
  })

  return (
    <EditForm
      onSubmit={handleSubmit((data) => save.mutate(data, { onSuccess: onClose }))}
      pending={save.isPending}
      error={save.error}
      onClose={onClose}
    >
      <Field id="edit-quantity" label="청약수량" error={errors.quantity?.message}>
        <MoneyInput
          id="edit-quantity"
          control={control}
          name="quantity"
          requiredMessage="청약수량을 입력해 주세요."
          validate={(v) => v > 0 || '청약수량은 1 이상이어야 합니다.'}
        />
      </Field>
      <Field id="edit-deposit" label="증거금" error={errors.depositAmount?.message}>
        <MoneyInput
          id="edit-deposit"
          control={control}
          name="depositAmount"
          requiredMessage="증거금을 입력해 주세요."
        />
      </Field>
      <Field id="edit-subscribedAt" label="청약일" error={errors.subscribedAt?.message}>
        <input
          id="edit-subscribedAt"
          type="date"
          {...register('subscribedAt', { required: '청약일을 입력해 주세요.' })}
        />
      </Field>
      <Field id="edit-memo" label="메모">
        <input id="edit-memo" type="text" {...register('memo')} />
      </Field>
    </EditForm>
  )
}

export function SubscriptionEditDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const query = useQuery({
    queryKey: investmentHistoryKeys.source('subscription', id),
    queryFn: ({ signal }) => getSubscription(id, signal),
  })
  return (
    <Shell title="청약 수정" query={query} onClose={onClose}>
      {(record) => <SubscriptionForm record={record} onClose={onClose} />}
    </Shell>
  )
}
