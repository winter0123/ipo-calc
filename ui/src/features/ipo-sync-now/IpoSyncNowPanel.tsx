import { toErrorMessage } from '@/shared/api/apiError.ts'
import { useIpoSyncNow } from './useIpoSyncNow.ts'
import styles from './IpoSyncNowPanel.module.css'

const ERROR_FALLBACK = '가져오기에 실패했습니다. 잠시 후 다시 시도해 주세요.'

export default function IpoSyncNowPanel() {
  const { mutate, isPending, isSuccess, data, error } = useIpoSyncNow()
  const errorMessage = toErrorMessage(error, {
    byStatus: {
      409: '이미 가져오기가 진행 중입니다. 잠시 후 다시 시도해 주세요.',
      503: 'OpenDART 키가 설정되지 않았습니다.',
    },
    fallback: ERROR_FALLBACK,
  })

  return (
    <section className={styles.panel} aria-label="DART 공모주 가져오기">
      <p className={styles.description}>
        매일 새벽 자동으로 가져옵니다. 지금 바로 가져오려면 누르세요.
      </p>
      <button type="button" className={styles.button} disabled={isPending} onClick={() => mutate()}>
        {isPending ? '가져오는 중…' : 'DART에서 지금 가져오기'}
      </button>
      <div role="status" aria-live="polite" className={styles.status}>
        {isPending && '수십 초에서 수 분 걸릴 수 있습니다. 이 화면을 닫지 마세요.'}
        {isSuccess &&
          data &&
          `${data.aborted ? '중간에 중단됐습니다. ' : '완료: '}새로 등록 ${data.created}건 · 갱신 ${data.updated}건 · 상세 ${data.detailSaved}건 · 실패 ${data.failed}건`}
      </div>
      {errorMessage && (
        <div role="alert" className={styles.error}>
          {errorMessage}
        </div>
      )}
    </section>
  )
}
