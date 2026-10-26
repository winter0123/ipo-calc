import { useState } from 'react'
import { useBrokerCredential } from '@/entities/broker/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import BrokerKeyForm, { type KeyFormValues } from './BrokerKeyForm.tsx'
import {
  useDisconnectBrokerCredential,
  useRegisterBrokerCredential,
  useReplaceBrokerCredential,
} from './useBrokerConnect.ts'
import styles, { StyleScope } from './BrokerConnectPanel.styles.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'

const STATUS_LABEL = {
  CONNECTED: '연결됨',
  EXPIRED: '사용기간 만료',
  ERROR: '연결 오류',
  DISCONNECTED: '연결 안 됨',
} as const

// 증권사별 연결 전 안내. 확인된 사실만 적는다.
const PROVIDER_NOTICE: Record<string, string> = {
  KIWOOM: '키움 REST API 사용 신청과 서버 IP 등록이 필요합니다.',
  LS: 'LS증권 OPEN API 사용 신청이 필요하며, 한 번에 31일까지 조회할 수 있습니다.',
}

const EXPIRY_WARNING_DAYS = 30
const DAY_MS = 24 * 60 * 60 * 1000

function daysUntil(isoDate: string, today: string) {
  return Math.round((Date.parse(isoDate) - Date.parse(today)) / DAY_MS)
}

export default function BrokerConnectPanel({
  securitiesAccountId,
}: {
  securitiesAccountId: number
}) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [replacing, setReplacing] = useState(false)
  const { data, isLoading, error: loadError } = useBrokerCredential(securitiesAccountId)
  const register = useRegisterBrokerCredential(securitiesAccountId)
  const replace = useReplaceBrokerCredential(securitiesAccountId)
  const disconnect = useDisconnectBrokerCredential(securitiesAccountId)
  const today = toLocalYmd(new Date())

  if (isLoading) return <p role="status">연결 상태를 불러오는 중...</p>
  if (loadError || !data) {
    return <p role="alert">연결 상태를 불러오지 못했습니다.</p>
  }

  const connected = data.connectionStatus !== 'DISCONNECTED'
  const provider = data.supportedProvider
  // 사용기간 1년 규칙은 한국투자증권(KIS)만 확인됐다. 다른 증권사는 만료 안내를 보여 주지 않는다.
  const isKis = (data.apiProvider ?? provider) === 'KIS'
  const providerNotice = PROVIDER_NOTICE[data.apiProvider ?? provider ?? '']
  const keyErrors = {
    400: (e: { serverMessage?: string }) => e.serverMessage,
    403: '이 증권계좌에 대한 권한이 없습니다.',
    404: '연결된 증권사 API가 없습니다.',
    409: '이미 연결된 증권계좌입니다.',
    503: '서버에 증권사 연동 설정이 없어 지금은 처리할 수 없습니다.',
  }
  const registerError = toErrorMessage(register.error, { byStatus: keyErrors })
  const replaceError = toErrorMessage(replace.error, { byStatus: keyErrors })
  const disconnectError = toErrorMessage(disconnect.error)

  const daysLeft = data.keyExpiresOn ? daysUntil(data.keyExpiresOn, today) : null
  const expiryNotice =
    daysLeft === null
      ? null
      : daysLeft < 0
        ? `증권사 서비스 사용기간이 ${data.keyExpiresOn}에 끝났습니다. 증권사에서 서비스를 갱신하고 새 키로 교체해 주세요.`
        : daysLeft <= EXPIRY_WARNING_DAYS
          ? `증권사 서비스 사용기간이 ${data.keyExpiresOn}에 끝납니다(${daysLeft}일 남음). 갱신하면 키가 새로 발급되니 그때 교체해 주세요.`
          : null

  const toBody = ({ appKey, appSecret, keyIssuedOn }: KeyFormValues) => ({
    appKey: appKey.trim(),
    appSecret: appSecret.trim(),
    keyIssuedOn: keyIssuedOn || undefined,
  })
  const submitRegister = (values: KeyFormValues) =>
    register.mutate({ ...toBody(values), apiProvider: provider! })
  const submitReplace = (values: KeyFormValues) =>
    replace.mutate(toBody(values), { onSuccess: () => setReplacing(false) })

  return (
    <StyleScope>
      {
        <section className={styles.panel} aria-labelledby="broker-connect-title">
          <h2 id="broker-connect-title" className={styles.title}>
            증권사 API 연결
          </h2>
          <p className={styles.notice}>
            조회 전용으로만 사용됩니다. 주문·정정·취소는 하지 않으며, 입력한 키는 암호화해 저장하고
            화면에 다시 보여주지 않습니다. 접근토큰은 서버가 자동으로 갱신합니다.
            {isKis && ' 증권사 서비스 사용기간은 신청일부터 1년입니다.'}
          </p>
          {providerNotice && <p className={styles.notice}>{providerNotice}</p>}
          <p>
            상태: <strong>{STATUS_LABEL[data.connectionStatus]}</strong>
            {data.accountNumber && <span> · 계좌 {data.accountNumber}</span>}
            {data.keyExpiresOn && <span> · 사용기간 만료일 {data.keyExpiresOn}</span>}
            {data.lastSyncedAt && (
              <span> · 마지막 동기화 {data.lastSyncedAt.slice(0, 16).replace('T', ' ')}</span>
            )}
          </p>
          {expiryNotice && (
            <p role="alert" className={styles.warning}>
              {expiryNotice}
            </p>
          )}

          {connected ? (
            <>
              <div className={styles.actions}>
                <Button variant="secondary" onClick={() => setReplacing((v) => !v)}>
                  {replacing ? '키 교체 취소' : '키 교체'}
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setConfirmOpen(true)}
                  disabled={disconnect.isPending}
                >
                  연결 해제
                </Button>
              </div>
              {replacing && (
                <BrokerKeyForm
                  idPrefix="broker-replace"
                  submitLabel="새 키로 교체"
                  pendingLabel="교체 중..."
                  pending={replace.isPending}
                  errorMessage={replaceError}
                  today={today}
                  showIssuedOn={isKis}
                  onSubmit={submitReplace}
                />
              )}
              {disconnectError && <p role="alert">{disconnectError}</p>}
              <ConfirmDialog
                isOpen={confirmOpen}
                title="연결 해제"
                message="증권사 API 연결을 해제할까요? 저장된 키가 삭제되며, 다시 쓰려면 키를 새로 입력해야 합니다."
                confirmLabel="해제"
                onCancel={() => setConfirmOpen(false)}
                onConfirm={() => {
                  setConfirmOpen(false)
                  disconnect.mutate()
                }}
              />
            </>
          ) : provider ? (
            <BrokerKeyForm
              idPrefix="broker"
              submitLabel="연결"
              pendingLabel="연결 중..."
              pending={register.isPending}
              errorMessage={registerError}
              today={today}
              showIssuedOn={isKis}
              onSubmit={submitRegister}
            />
          ) : (
            <p role="status">이 증권계좌의 증권사는 아직 API 연동을 지원하지 않습니다.</p>
          )}
        </section>
      }
    </StyleScope>
  )
}
