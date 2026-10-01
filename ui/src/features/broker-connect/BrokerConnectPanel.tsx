import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useBrokerCredential } from '@/entities/broker/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { useDisconnectBrokerCredential, useRegisterBrokerCredential } from './useBrokerConnect.ts'
import styles from './BrokerConnectPanel.module.css'

const PROVIDER = 'KIS'

const STATUS_LABEL = {
  CONNECTED: '연결됨',
  EXPIRED: '인증 만료',
  ERROR: '연결 오류',
  DISCONNECTED: '연결 안 됨',
} as const

interface FormValues {
  appKey: string
  appSecret: string
}

export default function BrokerConnectPanel({
  securitiesAccountId,
}: {
  securitiesAccountId: number
}) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const [confirmOpen, setConfirmOpen] = useState(false)
  const { data, isLoading, error: loadError } = useBrokerCredential(securitiesAccountId)
  const register = useRegisterBrokerCredential(securitiesAccountId)
  const disconnect = useDisconnectBrokerCredential(securitiesAccountId)
  const {
    register: field,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ mode: 'onSubmit' })

  if (isLoading) return <p role="status">연결 상태를 불러오는 중...</p>
  if (loadError || !data) {
    return <p role="alert">연결 상태를 불러오지 못했습니다.</p>
  }

  const connected = data.connectionStatus !== 'DISCONNECTED'
  const formErrorMessage = toErrorMessage(register.error, {
    byStatus: {
      403: '이 증권계좌에 대한 권한이 없습니다.',
      409: '이미 연결된 증권계좌입니다.',
      503: '서버에 증권사 연동 설정이 없어 지금은 연결할 수 없습니다.',
    },
  })
  const disconnectErrorMessage = toErrorMessage(disconnect.error)

  const onSubmit = (values: FormValues) => {
    register.mutate(
      { apiProvider: PROVIDER, appKey: values.appKey.trim(), appSecret: values.appSecret.trim() },
      { onSuccess: () => reset() },
    )
  }

  return (
    <section className={styles.panel} aria-labelledby="broker-connect-title">
      <h2 id="broker-connect-title" className={styles.title}>
        증권사 API 연결
      </h2>
      <p className={styles.notice}>
        조회 전용으로만 사용됩니다. 주문·정정·취소는 하지 않으며, 입력한 키는 암호화해 저장하고
        화면에 다시 보여주지 않습니다. 한국투자증권 서비스 사용기간은 신청일부터 1년이며, 갱신하면
        키가 새로 발급되니 그때 다시 연결해 주세요. 접근토큰(1일)은 서버가 자동으로 갱신합니다.
      </p>
      <p>
        상태: <strong>{STATUS_LABEL[data.connectionStatus]}</strong>
        {data.accountNumber && <span> · 계좌 {data.accountNumber}</span>}
        {data.lastSyncedAt && (
          <span> · 마지막 동기화 {data.lastSyncedAt.slice(0, 16).replace('T', ' ')}</span>
        )}
      </p>

      {connected ? (
        <>
          <Button
            variant="secondary"
            onClick={() => setConfirmOpen(true)}
            disabled={disconnect.isPending}
          >
            연결 해제
          </Button>
          {disconnectErrorMessage && <p role="alert">{disconnectErrorMessage}</p>}
          <ConfirmDialog
            isOpen={confirmOpen}
            title="연결 해제"
            message="증권사 API 연결을 해제할까요? 해제하면 조회에 쓰이지 않으며, 다시 쓰려면 키를 새로 입력해야 합니다."
            confirmLabel="해제"
            onCancel={() => setConfirmOpen(false)}
            onConfirm={() => {
              setConfirmOpen(false)
              disconnect.mutate()
            }}
          />
        </>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className={styles.form} noValidate>
          <div className={styles.field}>
            <label htmlFor="broker-app-key">앱키</label>
            <input
              id="broker-app-key"
              type="password"
              autoComplete="off"
              aria-invalid={errors.appKey ? 'true' : undefined}
              aria-describedby={errors.appKey ? 'broker-app-key-error' : undefined}
              {...field('appKey', { required: '앱키를 입력해 주세요.' })}
            />
            {errors.appKey && (
              <span id="broker-app-key-error" role="alert" className={styles.error}>
                {errors.appKey.message}
              </span>
            )}
          </div>
          <div className={styles.field}>
            <label htmlFor="broker-app-secret">앱시크릿</label>
            <input
              id="broker-app-secret"
              type="password"
              autoComplete="off"
              aria-invalid={errors.appSecret ? 'true' : undefined}
              aria-describedby={errors.appSecret ? 'broker-app-secret-error' : undefined}
              {...field('appSecret', { required: '앱시크릿을 입력해 주세요.' })}
            />
            {errors.appSecret && (
              <span id="broker-app-secret-error" role="alert" className={styles.error}>
                {errors.appSecret.message}
              </span>
            )}
          </div>
          {formErrorMessage && (
            <p role="alert" className={styles.error}>
              {formErrorMessage}
            </p>
          )}
          <Button type="submit" disabled={register.isPending}>
            {register.isPending ? '연결 중...' : '연결'}
          </Button>
        </form>
      )}
    </section>
  )
}
