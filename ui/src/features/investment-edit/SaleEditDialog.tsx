import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { investmentHistoryKeys } from '@/entities/subscription/index.ts'
import { getSale, toSalePayload, updateSale, type Sale } from '@/entities/sale/index.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { useRecordMutation } from './useRecordMutation.ts'
import { EditForm, Field, Shell } from './editDialogParts.tsx'
import { ymd } from './editDialogHelpers.ts'

interface SaleValues {
  quantity: number
  price: number
  saleDate: string
  otherFees: number
  ipoFee: number
}

function SaleForm({ record, onClose }: { record: Sale; onClose: () => void }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const save = useRecordMutation((data: SaleValues) => updateSale(record.id, toSalePayload(data)))
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<SaleValues>({
    defaultValues: {
      quantity: record.quantity,
      price:
        record.price ??
        (record.amount != null ? Math.round(record.amount / record.quantity) : Number.NaN),
      saleDate: ymd(record.saleDate),
      otherFees: record.tax ?? 0,
      ipoFee: record.fee ?? 0,
    },
  })

  return (
    <EditForm
      onSubmit={handleSubmit((data) => save.mutate(data, { onSuccess: onClose }))}
      pending={save.isPending}
      error={save.error}
      onClose={onClose}
    >
      <Field id="edit-sale-quantity" label="매도수량" error={errors.quantity?.message}>
        <MoneyInput
          id="edit-sale-quantity"
          control={control}
          name="quantity"
          requiredMessage="매도수량을 입력해 주세요."
          validate={(v) => v > 0 || '매도수량은 1 이상이어야 합니다.'}
        />
      </Field>
      <Field id="edit-sale-price" label="매도단가" error={errors.price?.message}>
        <MoneyInput
          id="edit-sale-price"
          control={control}
          name="price"
          requiredMessage="매도단가를 입력해 주세요."
        />
      </Field>
      <Field id="edit-saleDate" label="매도일" error={errors.saleDate?.message}>
        <input
          id="edit-saleDate"
          type="date"
          {...register('saleDate', { required: '매도일을 입력해 주세요.' })}
        />
      </Field>
      <Field id="edit-otherFees" label="세금 및 기타">
        <MoneyInput id="edit-otherFees" control={control} name="otherFees" />
      </Field>
      <Field id="edit-ipoFee" label="공모주수수료">
        <MoneyInput id="edit-ipoFee" control={control} name="ipoFee" />
      </Field>
    </EditForm>
  )
}

export function SaleEditDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const query = useQuery({
    queryKey: investmentHistoryKeys.source('sale', id),
    queryFn: ({ signal }) => getSale(id, signal),
  })
  return (
    <Shell title="매도 수정" query={query} onClose={onClose}>
      {(record) => <SaleForm record={record} onClose={onClose} />}
    </Shell>
  )
}
