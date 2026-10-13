import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { investmentHistoryKeys } from '@/entities/subscription/index.ts'
import { getAllocation, updateAllocation, type Allocation } from '@/entities/allocation/index.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { useRecordMutation } from './useRecordMutation.ts'
import { EditForm, Field, Shell } from './editDialogParts.tsx'
import { at9, isNum, ymd } from './editDialogHelpers.ts'

interface AllocationValues {
  allocatedQuantity: number
  allocatedAt: string
  allocationPrice: number
  allocationMethod: string
  note: string
}

function AllocationForm({ record, onClose }: { record: Allocation; onClose: () => void }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const save = useRecordMutation((data: AllocationValues) =>
    updateAllocation(record.id, {
      allocatedQuantity: data.allocatedQuantity,
      allocatedAt: at9(data.allocatedAt),
      allocationPrice: isNum(data.allocationPrice) ? data.allocationPrice : undefined,
      allocationMethod: data.allocationMethod || undefined,
      note: data.note,
    }),
  )
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<AllocationValues>({
    defaultValues: {
      allocatedQuantity: record.allocatedQuantity,
      allocatedAt: ymd(record.allocatedAt),
      allocationPrice: record.allocationPrice ?? Number.NaN,
      allocationMethod: record.allocationMethod ?? '',
      note: record.note ?? '',
    },
  })

  return (
    <EditForm
      onSubmit={handleSubmit((data) => {
        // 서버의 부분 수정은 빈 값을 '안 바꿈'으로 받는다. 있던 값을 비우면 저장돼도 그대로 남으므로 막는다.
        if (record.allocationPrice != null && !isNum(data.allocationPrice)) {
          setError('allocationPrice', {
            message: '배정단가는 비울 수 없습니다. 값을 입력해 주세요.',
          })
          return
        }
        if (record.allocationMethod && !data.allocationMethod) {
          setError('allocationMethod', {
            message: '배정방식은 비울 수 없습니다. 하나를 골라 주세요.',
          })
          return
        }
        save.mutate(data, { onSuccess: onClose })
      })}
      pending={save.isPending}
      error={save.error}
      onClose={onClose}
    >
      <Field id="edit-allocatedQuantity" label="배정수량" error={errors.allocatedQuantity?.message}>
        <MoneyInput
          id="edit-allocatedQuantity"
          control={control}
          name="allocatedQuantity"
          requiredMessage="배정수량을 입력해 주세요."
        />
      </Field>
      <Field id="edit-allocatedAt" label="배정일" error={errors.allocatedAt?.message}>
        <input
          id="edit-allocatedAt"
          type="date"
          {...register('allocatedAt', { required: '배정일을 입력해 주세요.' })}
        />
      </Field>
      <Field
        id="edit-allocationPrice"
        label="배정단가 (선택)"
        error={errors.allocationPrice?.message}
      >
        <MoneyInput id="edit-allocationPrice" control={control} name="allocationPrice" />
      </Field>
      <Field
        id="edit-allocationMethod"
        label="배정방식 (선택)"
        error={errors.allocationMethod?.message}
      >
        <select id="edit-allocationMethod" {...register('allocationMethod')}>
          <option value="">배정방식 선택</option>
          <option value="PROPORTIONAL">비례배정</option>
          <option value="LOTTERY">추첨배정</option>
          <option value="FIRST_COME">선착순</option>
        </select>
      </Field>
      <Field id="edit-note" label="비고 (선택)">
        <input id="edit-note" type="text" {...register('note')} />
      </Field>
    </EditForm>
  )
}

export function AllocationEditDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const query = useQuery({
    queryKey: investmentHistoryKeys.source('allocation', id),
    queryFn: () => getAllocation(id),
  })
  return (
    <Shell title="배정 수정" query={query} onClose={onClose}>
      {(record) => <AllocationForm record={record} onClose={onClose} />}
    </Shell>
  )
}
