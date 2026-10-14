// OCR 자원(Tesseract.js 워커·코어 wasm·한국어 언어 데이터)을 node_modules에서 public/ocr 로 복사한다.
// 버전은 package.json이 고정한다. 외부 CDN을 쓰지 않으려는 자체 호스팅용이며 결과물은 커밋하지 않는다.
import { cpSync, mkdirSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'

const require = createRequire(import.meta.url)
const out = new URL('../public/ocr/', import.meta.url).pathname
const tessDir = dirname(require.resolve('tesseract.js/package.json'))
const coreDir = dirname(
  createRequire(join(tessDir, 'package.json')).resolve('tesseract.js-core/package.json'),
)
const langDir = dirname(require.resolve('@tesseract.js-data/kor/package.json'))

mkdirSync(join(out, 'core'), { recursive: true })
mkdirSync(join(out, 'lang'), { recursive: true })
cpSync(join(tessDir, 'dist/worker.min.js'), join(out, 'worker.min.js'))
// LSTM 전용 코어만(.wasm.js는 wasm 내장 — 브라우저 기능(SIMD)에 따라 셋 중 하나만 내려받는다)
for (const f of readdirSync(coreDir).filter((n) => /-lstm\.wasm\.js$/.test(n)))
  cpSync(join(coreDir, f), join(out, 'core', f))
cpSync(join(langDir, '4.0.0_best_int/kor.traineddata.gz'), join(out, 'lang/kor.traineddata.gz'))
console.log('OCR 자원 복사 완료 ->', out)
