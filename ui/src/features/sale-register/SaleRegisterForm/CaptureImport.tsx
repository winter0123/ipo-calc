import styles from './SaleRegisterForm.module.css'
import { useCaptureImport } from './useCaptureImport.ts'
import { won, type SaleForm } from './useSaleForm.ts'

const progressText = (p: NonNullable<ReturnType<typeof useCaptureImport>['progress']>) =>
  p.phase === 'loading'
    ? `읽기 엔진을 준비하는 중... ${Math.round(p.progress * 100)}% (처음 한 번은 한국어 데이터 약 2MB를 내려받아 오래 걸립니다)`
    : `캡처를 읽는 중... (${p.index + 1}/${p.total})`

/** 한국투자증권 실현손익 캡처로 매도 폼 채우기. 이미지는 이 브라우저 밖으로 나가지 않는다. */
export default function CaptureImport({ f }: { f: SaleForm }) {
  'use no memo'
  const c = useCaptureImport(f)
  const busy = c.progress !== null
  return (
    <section className={styles.capture} aria-labelledby="capture-title">
      <h2 id="capture-title" className={styles.captureTitle}>
        캡처로 채우기
      </h2>
      <div className={styles.field}>
        <label htmlFor="capture-files">한국투자증권·NH투자증권 실현손익 캡처 (여러 장 가능)</label>
        <input
          id="capture-files"
          type="file"
          accept="image/*"
          multiple
          disabled={busy || f.isPending || f.subscriptionsLoading}
          aria-describedby="capture-hint"
          onChange={(e) => {
            const files = Array.from(e.target.files ?? [])
            e.target.value = '' // 같은 파일을 다시 골라도 변경으로 인식
            if (files.length) void c.run(files)
          }}
        />
        <span id="capture-hint" className={styles.hint}>
          이미지는 서버로 보내지지 않고 이 기기에서만 읽습니다. 채워진 값을 확인하고 직접
          등록하세요.
        </span>
      </div>

      <div role="status" aria-live="polite" className={styles.hint}>
        {c.progress && progressText(c.progress)}
        {f.capturePending && '청약을 선택하면 캡처 값이 채워집니다.'}
      </div>
      {c.error && (
        <div className={styles.formError} role="alert">
          {c.error}
        </div>
      )}

      {c.rows.length > 1 && (
        <div role="group" aria-label="캡처에서 읽은 매도 기록">
          <p className={styles.hint}>어느 기록을 채울까요?</p>
          <ul className={styles.captureList}>
            {c.rows.map((row, i) => (
              <li key={i}>
                <button type="button" className={styles.captureItem} onClick={() => c.choose(row)}>
                  <strong>{row.record.stockName}</strong>
                  <span>
                    {row.record.saleDate ?? '날짜 미상'}
                    {row.record.realizedProfit !== undefined &&
                      ` · 실현손익 ${won(row.record.realizedProfit)}`}
                  </span>
                  {row.registered && <em>이미 등록된 매도로 보임</em>}
                  {row.matches.length === 0 && <em>맞는 청약 없음</em>}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {f.captureNotes.length > 0 && (
        <ul className={styles.captureNotes} aria-label="캡처 확인 사항">
          {f.captureNotes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
    </section>
  )
}
