import { useEffect, useState, type MutableRefObject } from 'react'
import type { UseFormGetValues, UseFormSetValue } from 'react-hook-form'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { won } from './saleFormUtils.ts'
import type { CaptureApply, CapturedField, SaleFormValues } from './saleFormTypes.ts'

interface UseCaptureFillOptions {
  selectedSubscriptionId?: number
  allocationData?: InvestmentHistoryItem
  ipoPending: boolean
  ipoPrice: number | null
  prefilledFor: MutableRefObject<number | undefined>
  setValue: UseFormSetValue<SaleFormValues>
  getValues: UseFormGetValues<SaleFormValues>
  setLastEdited: (value: 'fees' | 'profit') => void
  syncLinked: (override?: { source?: 'fees' | 'profit' }) => void
  selectSubscription: (id: number) => void
}

/** 캡처 입력을 대기하고 청약·공모주 데이터가 준비된 뒤 매도 폼에 적용한다. */
export function useCaptureFill({
  selectedSubscriptionId,
  allocationData,
  ipoPending,
  ipoPrice,
  prefilledFor,
  setValue,
  getValues,
  setLastEdited,
  syncLinked,
  selectSubscription,
}: UseCaptureFillOptions) {
  const [pendingCapture, setPendingCapture] = useState<CaptureApply | null>(null)
  const [captured, setCaptured] = useState<Partial<Record<CapturedField, number | string>>>({})
  const [captureNotes, setCaptureNotes] = useState<string[]>([])

  // 기본값 채우기가 끝난 뒤 실행돼야 캡처 값이 기본값에 덮이지 않는다.
  /* eslint-disable react-hooks/set-state-in-effect -- 청약 정보가 도착한 시점에 한 번만 적용한다 */
  useEffect(() => {
    if (!pendingCapture || !selectedSubscriptionId || !allocationData?.allocation || ipoPending)
      return
    if (prefilledFor.current !== selectedSubscriptionId) return
    if (pendingCapture.subscriptionId && pendingCapture.subscriptionId !== selectedSubscriptionId)
      return

    const { fill } = pendingCapture
    const notes = [...pendingCapture.notes]
    const mark: Partial<Record<CapturedField, number | string>> = {}
    const opt = { shouldDirty: true }
    if (fill.quantity !== undefined) setValue('quantity', (mark.quantity = fill.quantity), opt)
    if (fill.price !== undefined) setValue('price', (mark.price = fill.price), opt)
    if (fill.saleDate) setValue('saleDate', (mark.saleDate = fill.saleDate), opt)
    if (fill.realizedProfit !== undefined) {
      setValue('realizedProfit', (mark.realizedProfit = fill.realizedProfit), opt)
      setLastEdited('profit')
      if (fill.quantity === undefined || fill.price === undefined || ipoPrice == null) {
        setValue('otherFees', fill.fees ?? Number.NaN, opt)
        if (fill.fees !== undefined) mark.otherFees = fill.fees
      } else {
        syncLinked({ source: 'profit' })
        const derived = getValues('otherFees')
        if (fill.fees !== undefined) {
          if (derived !== fill.fees) {
            notes.push(
              `캡처의 수수료·제세금은 합계 ${won(fill.fees)}이지만, 실현손익과 취득 단가(${won(ipoPrice)}) 기준으로 계산한 세금 및 기타는 ${won(derived)}입니다. 실현손익을 우선해 ${won(derived)}으로 채웠습니다.`,
            )
          }
          mark.otherFees = derived
        }
      }
    } else if (fill.fees !== undefined) {
      setValue('otherFees', (mark.otherFees = fill.fees), opt)
      setLastEdited('fees')
      syncLinked({ source: 'fees' })
    }
    setCaptured(mark)
    setCaptureNotes(notes)
    setPendingCapture(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 대기 중인 캡처와 청약 정보가 바뀔 때만 실행한다
  }, [pendingCapture, allocationData, ipoPending, selectedSubscriptionId])
  /* eslint-enable react-hooks/set-state-in-effect */

  const applyCapture = (apply: CaptureApply) => {
    if (apply.subscriptionId && apply.subscriptionId !== selectedSubscriptionId) {
      selectSubscription(apply.subscriptionId)
    }
    setCaptured({})
    setCaptureNotes([])
    setPendingCapture(apply)
  }

  return {
    applyCapture,
    captured,
    captureNotes,
    capturePending: pendingCapture !== null && !selectedSubscriptionId,
  }
}
