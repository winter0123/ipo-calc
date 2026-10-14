// 브라우저 안 OCR(Tesseract.js). 이미지는 이 브라우저를 벗어나지 않는다.
// Tesseract 코드·wasm·한국어 언어 데이터는 모두 같은 출처(/ocr/*, scripts/copy-ocr.mjs가 빌드 때 복사)에서 받는다.

export interface OcrWord {
  t: string
  x0: number
  y0: number
  x1: number
  y1: number
}
export interface OcrPage {
  width: number
  height: number
  words: OcrWord[]
}
/** loading: 엔진·언어 데이터 준비(첫 실행은 수 MB 내려받음), recognizing: index번째 이미지 인식 중 */
export type OcrProgress =
  { phase: 'loading'; progress: number } | { phase: 'recognizing'; index: number; total: number }

const BASE = `${import.meta.env.BASE_URL}ocr`
const MAX_SIDE = 3000
const THRESHOLD = 170

/** 회색조 → 1%~99% 대비 늘리기 → 임계값 이진화. 원본 크기를 유지한다(검증된 구성). */
export function binarize(data: Uint8ClampedArray): void {
  const hist = new Array<number>(256).fill(0)
  const gray = new Uint8Array(data.length / 4)
  for (let i = 0; i < gray.length; i++) {
    const g = Math.round(0.299 * data[i * 4] + 0.587 * data[i * 4 + 1] + 0.114 * data[i * 4 + 2])
    gray[i] = g
    hist[g]++
  }
  const percentile = (p: number) => {
    let acc = 0
    for (let v = 0; v < 256; v++) {
      acc += hist[v]
      if (acc >= gray.length * p) return v
    }
    return 255
  }
  const lo = percentile(0.01)
  const hi = percentile(0.99)
  const span = Math.max(hi - lo, 1)
  for (let i = 0; i < gray.length; i++) {
    const stretched = Math.min(255, Math.max(0, ((gray[i] - lo) * 255) / span))
    const v = stretched >= THRESHOLD ? 255 : 0
    data[i * 4] = data[i * 4 + 1] = data[i * 4 + 2] = v
    data[i * 4 + 3] = 255
  }
}

async function preprocess(file: Blob): Promise<{ png: Blob; width: number; height: number }> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) throw new Error('이미지를 처리할 수 없습니다.')
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()
  const image = ctx.getImageData(0, 0, width, height)
  binarize(image.data)
  ctx.putImageData(image, 0, 0)
  const png = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
  if (!png) throw new Error('이미지를 처리할 수 없습니다.')
  return { png, width, height }
}

/**
 * 이미지들을 순서대로 인식해 단어 위치 목록을 돌려준다. tesseract.js는 이 호출 때 처음 내려받는다(지연 로드).
 * retryWhen: 페이지 분할 모드 4(한국투자증권에 정확)로 읽은 결과가 true를 돌려주면 모드 6(NH투자증권에 정확)으로 한 번 더 읽는다.
 */
export async function recognizeImages(
  files: Blob[],
  onProgress: (p: OcrProgress) => void,
  retryWhen?: (page: OcrPage) => boolean,
): Promise<OcrPage[]> {
  onProgress({ phase: 'loading', progress: 0 })
  const { createWorker } = await import('tesseract.js')
  const worker = await createWorker('kor', 1, {
    workerPath: `${BASE}/worker.min.js`,
    corePath: `${BASE}/core`,
    langPath: `${BASE}/lang`,
    workerBlobURL: false, // blob: 워커 없이 같은 출처 스크립트로 실행(CSP worker-src 'self'로 충분)
    logger: (m) => {
      if (m.status !== 'recognizing text') onProgress({ phase: 'loading', progress: m.progress })
    },
  })
  try {
    const pages: OcrPage[] = []
    for (let index = 0; index < files.length; index++) {
      onProgress({ phase: 'recognizing', index, total: files.length })
      const { png, width, height } = await preprocess(files[index])
      const read = async (psm: '4' | '6') => {
        await worker.setParameters({
          tessedit_pageseg_mode: psm as never,
          preserve_interword_spaces: '1',
        })
        const { data } = await worker.recognize(png, {}, { blocks: true })
        const words: OcrWord[] = []
        for (const b of data.blocks ?? [])
          for (const p of b.paragraphs)
            for (const l of p.lines) for (const w of l.words) words.push({ t: w.text, ...w.bbox })
        return { width, height, words }
      }
      let page = await read('4')
      if (retryWhen?.(page)) page = await read('6') // ponytail: 재시도는 한 번뿐
      pages.push(page)
    }
    return pages
  } finally {
    await worker.terminate()
  }
}
