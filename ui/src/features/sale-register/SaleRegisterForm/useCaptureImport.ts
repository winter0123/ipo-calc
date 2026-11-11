import { useState } from 'react'
import { useIpos } from '@/entities/ipo/index.ts'
import {
  captureChecks,
  captureToFill,
  looksRegistered,
  matchSubscriptions,
  mergeCaptureRecords,
  parseCapture,
  type CaptureRecord,
  type MatchCandidate,
} from '@/entities/sale/index.ts'
import { recognizeImages, type OcrProgress } from '@/shared/lib/ocr/recognizeImages.ts'
import type { SaleForm } from './useSaleForm.ts'

/** 캡처에서 읽은 매도 1건과 현재 투자자의 청약 매칭 결과. */
export interface CaptureRow {
  record: CaptureRecord
  matches: number[]
  registered: boolean
}

const FIELD_LABEL: Record<string, string> = {
  quantity: '매도 수량',
  salePrice: '체결가',
  saleAmount: '매도금액',
  buyPrice: '평균 매수가',
  fee: '수수료',
  tax: '제세금',
  realizedProfit: '실현손익',
}

const UNSUPPORTED = '한국투자증권·NH투자증권 앱의 실현손익 화면 캡처만 읽을 수 있습니다.'

/** 캡처 이미지 → 브라우저 안 OCR → 규칙 파서 → (1건이면 바로, 여러 건이면 고른 뒤) 매도 폼 채우기. */
export function useCaptureImport(f: SaleForm) {
  const { data: ipos } = useIpos()
  const [progress, setProgress] = useState<OcrProgress | null>(null)
  const [rows, setRows] = useState<CaptureRow[]>([])
  const [error, setError] = useState<string | null>(null)

  const candidates = (): MatchCandidate[] =>
    f.subscriptions
      .filter((item) => item.subscriptionStatus === 'ACTIVE')
      .map((item) => {
        const ipo = ipos?.find((i) => i.id === item.ipoId)
        return {
          subscriptionId: item.id,
          names: [item.ipoName, ipo?.ipoName, ipo?.companyName],
          stockCode: ipo?.stockCode,
          securitiesAccountId: item.securitiesAccountId ?? null,
          sales: item.sales
            .filter((s) => s.status === 'ACTIVE')
            .map((s) => ({ date: s.soldAt.slice(0, 10), quantity: s.quantity })),
        }
      })

  /** 캡처 기록이 현재 화면의 공모주와 같은 종목인지(종목코드 우선, 없으면 이름). */
  const matchesEntryIpo = (record: CaptureRecord): boolean => {
    if (f.effectiveIpoId == null) return true
    const entry: MatchCandidate = {
      subscriptionId: -1,
      names: [f.ipoName, f.ipoCompanyName],
      stockCode: f.ipoStockCode,
      sales: [],
    }
    return matchSubscriptions(record, [entry]).length > 0
  }

  const choose = (row: CaptureRow) => {
    const { record } = row
    const notes: string[] = []
    if (record.corrected?.length)
      notes.push(
        `검산으로 ${record.corrected.map((k) => FIELD_LABEL[k] ?? k).join('·')} 값을 고쳤습니다. 캡처와 맞는지 확인하세요.`,
      )
    const [e1, e2] = captureChecks(record)
    if (e1 === false || e2 === false)
      notes.push('읽은 숫자가 서로 맞지 않습니다(수량×체결가, 손익 검산). 값을 직접 확인하세요.')
    // 필수 문맥(공모주)이 아직 확정되지 않았으면 채우지 않고 즉시 안내한다
    if (f.effectiveIpoId == null) {
      notes.push('공모주 정보를 확인하는 중입니다. 잠시 후 다시 시도해 주세요.')
      f.noteCapture(notes)
      setRows([])
      return
    }
    if (!matchesEntryIpo(record)) {
      // 다른 공모주의 캡처를 현재 화면에 넣으면 엉뚱한 매도가 등록된다 — 채우지 않고 즉시 안내한다
      notes.push(
        `캡처의 종목('${record.stockName}')은 이 화면의 공모주가 아닙니다. 종목을 확인해 주세요.`,
      )
      f.noteCapture(notes)
      setRows([])
      return
    }
    const fill = captureToFill(record)
    if (fill.quantity === undefined || fill.price === undefined)
      notes.push('이 캡처에서는 매도수량이나 매도단가를 읽지 못했습니다. 직접 입력하세요.')
    if (fill.saleDate === undefined)
      notes.push(
        '이 캡처에는 매도일이 없습니다. 매도일을 확인해 직접 고치세요(상장일이 들어 있습니다).',
      )
    if (row.registered)
      notes.push('같은 날짜·수량의 매도가 이미 등록돼 있어 보입니다. 중복인지 확인하세요.')
    // 계좌·연결 문맥까지 지킨다: 다른 계좌·청약에 대응하는 캡처라도 자동 전환하지 않고 값만 채운다
    const selectedAccountId = f.securitiesAccountId
    const matchedCandidate =
      row.matches.length === 1
        ? candidates().find((c) => c.subscriptionId === row.matches[0])
        : undefined
    const accountConflict =
      matchedCandidate != null &&
      selectedAccountId != null &&
      matchedCandidate.securitiesAccountId != null &&
      matchedCandidate.securitiesAccountId !== selectedAccountId
    const connectionConflict =
      matchedCandidate != null &&
      f.connectionId != null &&
      matchedCandidate.subscriptionId !== f.connectionId
    if (accountConflict) {
      notes.push(
        '캡처는 다른 계좌의 청약에 대응합니다. 연결을 바꾸지 않고 현재 계좌 기준으로 채웠습니다. 계좌를 확인해 주세요.',
      )
    } else if (connectionConflict) {
      notes.push(
        '캡처는 이 화면의 청약과 다른 청약에 대응합니다. 연결을 바꾸지 않고 값만 채웠습니다.',
      )
    } else if (row.matches.length === 0) {
      notes.push('이 공모주에 연결된 청약 기록을 찾지 못해, 현재 화면 기준으로 채웠습니다.')
    } else if (row.matches.length > 1) {
      notes.push(
        '같은 종목의 기록이 여러 건이라 현재 연결된 계좌 기준으로 채웠습니다. 계좌를 확인해 주세요.',
      )
    }
    f.applyCapture({
      // 현재 문맥(공모주·계좌·연결)을 유지한다 — 캡처만으로 연결을 갈아끼우지 않는다
      subscriptionId: undefined,
      fill,
      notes,
    })
    setRows([])
  }

  const run = async (files: File[]) => {
    setError(null)
    setRows([])
    f.applyCapture({ fill: {}, notes: [] }) // 이전 캡처의 표시를 지운다(연결은 그대로)
    try {
      const fallbackYear = new Date().getFullYear()
      const parse = (page: Awaited<ReturnType<typeof recognizeImages>>[number]) =>
        parseCapture(page.words, { width: page.width, height: page.height, fallbackYear })
      const pages = await recognizeImages(files, setProgress, (p) => {
        const { kind, broker } = parse(p)
        return kind === 'unknown' || broker === 'NH' // NH는 페이지 분할 모드 6이 정확하다
      })
      const parsed = pages.map(parse)
      if (parsed.every((p) => p.kind === 'unknown')) return setError(UNSUPPORTED)
      const records = mergeCaptureRecords(parsed.map((p) => p.records))
      if (!records.length) return setError('캡처에서 매도 기록을 찾지 못했습니다. ' + UNSUPPORTED)
      const cands = candidates()
      const result = records.map((record) => {
        const matches = matchSubscriptions(record, cands)
        const registered = matches.some((id) =>
          looksRegistered(
            record,
            cands.find((c) => c.subscriptionId === id)!,
          ),
        )
        return { record, matches, registered }
      })
      if (result.length === 1) choose(result[0])
      else setRows(result)
    } catch (e) {
      console.error(e)
      setError('캡처를 읽는 중 문제가 생겼습니다. 같은 화면을 다시 올리거나 직접 입력해 주세요.')
    } finally {
      setProgress(null)
    }
  }

  return { progress, rows, error, run, choose }
}
