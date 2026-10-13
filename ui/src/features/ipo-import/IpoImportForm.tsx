import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useIpoImport } from './useIpoImport.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { buildTemplateBlob, CSV_HEADERS, MAX_CSV_BYTES, REQUIRED_HEADERS } from './csvTemplate.ts'
import styles from './IpoImportForm.module.css'

const SIZE_MESSAGE = 'CSV 파일은 최대 5MB까지 업로드할 수 있습니다.'

function downloadTemplate() {
  const url = URL.createObjectURL(buildTemplateBlob())
  const a = document.createElement('a')
  a.href = url
  a.download = 'ipo-import-template.csv'
  a.click()
  URL.revokeObjectURL(url)
}

export default function IpoImportForm() {
  const { mutate, isPending, error, data, reset } = useIpoImport()
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  const onChange = (e: ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null
    reset()
    if (picked && picked.size > MAX_CSV_BYTES) {
      setFile(null)
      setFileError(SIZE_MESSAGE)
      return
    }
    setFileError(null)
    setFile(picked)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (file) mutate(file)
  }

  const serverError = toErrorMessage(error, {
    byStatus: {
      400: (e) => e.serverMessage,
      403: '관리자만 공모주를 가져올 수 있습니다.',
      413: (e) => e.serverMessage ?? SIZE_MESSAGE,
    },
    fallback: 'CSV를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  })
  const message = fileError ?? serverError

  return (
    <div className={styles.wrap}>
      <section className={styles.guide} aria-label="CSV 형식 안내">
        <p>
          첫 줄은 헤더여야 합니다. 필수 헤더: <strong>{REQUIRED_HEADERS.join(', ')}</strong>
        </p>
        <p>전체 헤더</p>
        <code className={styles.code}>{CSV_HEADERS.join(',')}</code>
        <p>예시 한 줄</p>
        <code className={styles.code}>
          예시전자,예시전자 공모,123456,,10000,12000,,2026-11-02,2026-11-03,2026-11-12,1000000
        </code>
        <p>
          날짜는 YYYY-MM-DD 형식입니다. 엑셀에서는 &apos;CSV UTF-8&apos; 또는 일반 CSV로 저장해
          주세요(둘 다 지원). 파일은 최대 5MB입니다.
        </p>
        <button type="button" className={styles.secondary} onClick={downloadTemplate}>
          예시 CSV 내려받기
        </button>
      </section>

      <form className={styles.form} onSubmit={onSubmit} aria-label="공모주 CSV 가져오기 폼">
        <label htmlFor="ipo-csv">CSV 파일</label>
        <input
          id="ipo-csv"
          type="file"
          accept=".csv,text/csv"
          onChange={onChange}
          disabled={isPending}
        />
        {message && (
          <div className={styles.formError} role="alert">
            {message}
          </div>
        )}
        <button type="submit" className={styles.submit} disabled={isPending || !file}>
          {isPending ? '가져오는 중...' : '가져오기'}
        </button>
      </form>

      {data && (
        <section aria-label="가져오기 결과" className={styles.result}>
          <p role="status">
            성공 {data.successCount}건 · 실패 {data.failureCount}건
          </p>
          {data.failures.length > 0 && (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">행 번호</th>
                  <th scope="col">사유</th>
                </tr>
              </thead>
              <tbody>
                {data.failures.map((f) => (
                  <tr key={f.rowNumber}>
                    <td>{f.rowNumber}</td>
                    <td>{f.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  )
}
