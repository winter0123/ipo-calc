import { useEffect, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import {
  getSubscription,
  investmentHistoryKeys,
  updateSubscription,
  type Subscription,
} from '@/entities/subscription/index.ts'
import { getAllocation, updateAllocation, type Allocation } from '@/entities/allocation/index.ts'
import { getSale, updateSale, type Sale } from '@/entities/sale/index.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { useRecordMutation } from './useRecordMutation.ts'
import styles from './RecordActions.module.css'

const isNum = (value: unknown): value is number => typeof value === 'number' && !Number.isNaN(value)
const ymd = (value: string) => value.slice(0, 10)
/** 등록 화면과 같이 날짜만 받아 T09:00을 붙여 보낸다 */
const at9 = (date: string) => `${date}T09:00`

/** 레코드를 읽어 온 뒤 폼을 보여 주는 대화상자 껍데기. */
function Shell<T>({
  title,
  query,
  onClose,
  children,
}: {
  title: string
  query: UseQueryResult<T>
  onClose: () => void
  children: (record: T) => ReactNode
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className={styles.overlay}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-label={title}>
        <h2 className={styles.dialogTitle}>{title}</h2>
        {query.data ? (
          children(query.data)
        ) : query.isError ? (
          <p role="alert" className={styles.error}>
            {toErrorMessage(query.error)}
          </p>
        ) : (
          <p>불러오는 중…</p>
        )}
        {!query.data && (
          <div className={styles.formActions}>
            <button type="button" className={styles.smallButton} onClick={onClose}>
              닫기
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className={styles.field}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error && (
        <span className={styles.fieldError} aria-live="polite">
          {error}
        </span>
      )}
    </div>
  )
}

function FormButtons({
  pending,
  error,
  onClose,
}: {
  pending: boolean
  error: unknown
  onClose: () => void
}) {
  return (
    <>
      {!!error && (
        <p role="alert" className={styles.error}>
          {toErrorMessage(error)}
        </p>
      )}
      <div className={styles.formActions}>
        <button type="button" className={styles.smallButton} onClick={onClose}>
          취소
        </button>
        <button type="submit" className={styles.smallButton} disabled={pending}>
          {pending ? '저장 중…' : '저장'}
        </button>
      </div>
    </>
  )
}

/* ---------- 청약 ---------- */

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
    <form
      className={styles.form}
      onSubmit={handleSubmit((data) => save.mutate(data, { onSuccess: onClose }))}
      noValidate
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
      <FormButtons pending={save.isPending} error={save.error} onClose={onClose} />
    </form>
  )
}

export function SubscriptionEditDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const query = useQuery({
    queryKey: investmentHistoryKeys.source('subscription', id),
    queryFn: () => getSubscription(id),
  })
  return (
    <Shell title="청약 수정" query={query} onClose={onClose}>
      {(record) => <SubscriptionForm record={record} onClose={onClose} />}
    </Shell>
  )
}

/* ---------- 배정 ---------- */

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
    <form
      className={styles.form}
      onSubmit={handleSubmit((data) => save.mutate(data, { onSuccess: onClose }))}
      noValidate
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
      <Field id="edit-allocationPrice" label="배정단가 (선택)">
        <MoneyInput id="edit-allocationPrice" control={control} name="allocationPrice" />
      </Field>
      <Field id="edit-allocationMethod" label="배정방식 (선택)">
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
      <FormButtons pending={save.isPending} error={save.error} onClose={onClose} />
    </form>
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

/* ---------- 매도 ---------- */

interface SaleValues {
  quantity: number
  price: number
  saleDate: string
  otherFees: number
  ipoFee: number
}

function SaleForm({ record, onClose }: { record: Sale; onClose: () => void }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 등록 화면과 같은 매핑: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타
  const save = useRecordMutation((data: SaleValues) =>
    updateSale(record.id, {
      quantity: data.quantity,
      price: data.price,
      amount: data.price * data.quantity,
      saleDate: at9(data.saleDate),
      fee: isNum(data.ipoFee) ? data.ipoFee : 0,
      tax: isNum(data.otherFees) ? data.otherFees : 0,
    }),
  )
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
    <form
      className={styles.form}
      onSubmit={handleSubmit((data) => save.mutate(data, { onSuccess: onClose }))}
      noValidate
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
      <FormButtons pending={save.isPending} error={save.error} onClose={onClose} />
    </form>
  )
}

export function SaleEditDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const query = useQuery({
    queryKey: investmentHistoryKeys.source('sale', id),
    queryFn: () => getSale(id),
  })
  return (
    <Shell title="매도 수정" query={query} onClose={onClose}>
      {(record) => <SaleForm record={record} onClose={onClose} />}
    </Shell>
  )
}
