import { useEffect, useRef, useState, type MutableRefObject } from 'react'
import type { UseFormGetValues, UseFormSetValue } from 'react-hook-form'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { won } from './saleFormUtils.ts'
import type { CaptureApply, CapturedField, SaleFormValues } from './saleFormTypes.ts'

interface UseCaptureFillOptions {
  connectionId?: number
  allocationData?: InvestmentHistoryItem
  ipoPending: boolean
  /** 폼 문맥(공모주·연결)이 확정돼 캡처 값을 적용할 수 있는지 */
  connectionReady: boolean
  /** 연결 문맥이 바뀔 때 캡처 표시를 지우기 위한 계좌 ID(선택) */
  accountId?: number
  ipoPrice: number | null
  prefilledFor: MutableRefObject<number | undefined>
  setValue: UseFormSetValue<SaleFormValues>
  getValues: UseFormGetValues<SaleFormValues>
  setLastEdited: (value: 'fees' | 'profit') => void
  syncLinked: (override?: { source?: 'fees' | 'profit' }) => void
  selectSubscription: (id: number) => void
}

/** 캡처 입력을 대기하고 공모주·연결 데이터가 준비된 뒤 매도 폼에 적용한다. */
export function useCaptureFill({
  connectionId,
  allocationData,
  ipoPending,
  connectionReady,
  accountId,
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

  // 연결·계좌 문맥이 바뀌면 "캡처에서 채움" 표시와 대기 중인 캡처를 지운다(새 문맥 기준으로 다시 채울 수 있도록)
  const previousContext = useRef<{ connectionId?: number; accountId?: number }>({})
  useEffect(() => {
    const prev = previousContext.current
    if (prev.connectionId === connectionId && prev.accountId === accountId) return
    previousContext.current = { connectionId, accountId }
    if (prev.connectionId === undefined && prev.accountId === undefined) return
    setCaptured({})
    setCaptureNotes([])
    setPendingCapture(null)
  }, [connectionId, accountId])

  // 기본값 채우기가 끝난 뒤 실행돼야 캡처 값이 기본값에 덮이지 않는다.
  /* eslint-disable react-hooks/set-state-in-effect -- 연결 정보가 준비된 시점에 한 번만 적용한다 */
  useEffect(() => {
    if (!pendingCapture || !connectionReady || ipoPending) return
    // 캡처가 찾은 청약으로 바뀌는 중이면 바뀐 뒤 다시 적용한다
    if (pendingCapture.subscriptionId && pendingCapture.subscriptionId !== connectionId) return
    // 기존 배정이 있으면 기본값 채우기가 끝난 뒤에만 적용한다
    if (connectionId && allocationData?.allocation && prefilledFor.current !== connectionId) return

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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 대기 중인 캡처와 연결 정보가 바뀔 때만 실행한다
  }, [pendingCapture, allocationData, ipoPending, connectionReady, connectionId])
  /* eslint-enable react-hooks/set-state-in-effect */

  /** 문맥을 확정할 수 없을 때의 즉시 안내(폼 입력은 건드리지 않고 메모만 보여 준다). */
  const noteCapture = (notes: string[]) => {
    setCaptured({})
    setCaptureNotes(notes)
    setPendingCapture(null)
  }
  const applyCapture = (apply: CaptureApply) => {
    if (apply.subscriptionId && apply.subscriptionId !== connectionId) {
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
    capturePending: pendingCapture !== null,
    noteCapture,
  }
}
