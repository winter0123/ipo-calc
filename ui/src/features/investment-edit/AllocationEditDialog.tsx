import { useForm, useWatch } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { investmentHistoryKeys } from '@/entities/subscription/index.ts'
import { getAllocation, updateAllocation, type Allocation } from '@/entities/allocation/index.ts'
import { useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { useRecordMutation } from './useRecordMutation.ts'
import { EditForm, Field, Shell } from './editDialogParts.tsx'

interface AllocationValues {
  allocatedQuantity: number
  note: string
}

function AllocationForm({ record, onClose }: { record: Allocation; onClose: () => void }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const save = useRecordMutation((data: AllocationValues) =>
    updateAllocation(record.id, {
      allocatedQuantity: data.allocatedQuantity,
      note: data.note,
    }),
  )
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<AllocationValues>({
    defaultValues: {
      allocatedQuantity: record.allocatedQuantity,
      note: record.note ?? '',
    },
  })

  // 배정 총액 실시간 계산: useInvestmentHistoryDetail로 ipoFixedPrice를 가져와서 fixedPrice × 수량 계산
  const { data: subDetail } = useInvestmentHistoryDetail(record.subscriptionId)
  const fixedPrice = subDetail?.ipoFixedPrice ?? null
  const watchedQuantity = useWatch({ control, name: 'allocatedQuantity' })
  const quantity = Number.isFinite(watchedQuantity) && watchedQuantity > 0 ? watchedQuantity : 0
  const allocationTotal = fixedPrice != null && quantity > 0 ? fixedPrice * quantity : null

  return (
    <EditForm
      onSubmit={handleSubmit((data) => {
        // 총액/배정일/배정단가는 서버로 보내지 않는다.
        save.mutate(data, { onSuccess: onClose })
      })}
      pending={save.isPending}
      error={save.error}
      onClose={onClose}
    >
      <Field id="edit-allocatedQuantity" label="배정수량" error={errors.allocatedQuantity?.message}>
        <input
          id="edit-allocatedQuantity"
          type="number"
          inputMode="numeric"
          min="0"
          {...register('allocatedQuantity', {
            valueAsNumber: true,
            validate: (v) =>
              v === undefined ||
              v === null ||
              (Number.isInteger(Number(v)) && Number(v) >= 0) ||
              '0 이상 정수를 입력해 주세요.',
          })}
          disabled={save.isPending}
        />
      </Field>
      <Field id="edit-allocatedAmount" label="배정 총액">
        <output id="edit-allocatedAmount" aria-label="배정 총액">
          {allocationTotal != null
            ? formatWon(allocationTotal)
            : '확정 공모가가 없어 자동 계산할 수 없습니다.'}
        </output>
        <span className="hint">
          확정 공모가 × 배정수량으로 자동 계산됩니다. 서버에 금액을 보내지 않습니다.
        </span>
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
    queryFn: ({ signal }) => getAllocation(id, signal),
  })
  return (
    <Shell title="배정 수정" query={query} onClose={onClose}>
      {(record) => <AllocationForm record={record} onClose={onClose} />}
    </Shell>
  )
}
