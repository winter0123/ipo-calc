import { useState } from 'react'
import {
  sendTestNotification,
  usePreferences,
  useSavePreferences,
  useVapidKey,
  type NotificationPreferences,
} from '@/entities/notification/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import { usePushDevice } from './usePushDevice.ts'
import styles, { StyleScope } from './NotificationSettings.styles.ts'

const KINDS: { key: keyof NotificationPreferences; label: string; help: string }[] = [
  {
    key: 'subscriptionDeadline',
    label: '청약 마감 당일 알림',
    help: '오늘 청약이 마감되는 공모주를 아침 8시 30분에 알려 드립니다.',
  },
  {
    key: 'listingDay',
    label: '상장일 알림',
    help: '내 배정 중 아직 팔지 않은 수량이 있는 공모주의 상장일 아침에 알려 드립니다.',
  },
]

export default function NotificationSettings() {
  const vapid = useVapidKey()
  const prefs = usePreferences()
  const save = useSavePreferences()
  const enabled = vapid.data?.enabled === true
  const device = usePushDevice(vapid.data?.publicKey)
  const [testMessage, setTestMessage] = useState<string | null>(null)
  const [testing, setTesting] = useState(false)
  // 쿼리 캐시 갱신은 한 박자 늦게 화면에 닿아 체크박스가 되돌아가 보이므로, 누른 값을 바로 보여 주는 임시값을 둔다
  const [draft, setDraft] = useState<NotificationPreferences | null>(null)

  if (vapid.isLoading || prefs.isLoading) return <p role="status">불러오는 중...</p>
  if (vapid.error || prefs.error || !vapid.data || !prefs.data) {
    return <p role="alert">알림 설정을 불러오지 못했습니다.</p>
  }

  const current = draft ?? prefs.data
  const toggle = (key: keyof NotificationPreferences, checked: boolean) => {
    const next = { ...current, [key]: checked }
    setDraft(next)
    save.mutate(next, { onError: () => setDraft(null) })
  }

  const runTest = async () => {
    setTesting(true)
    setTestMessage(null)
    try {
      const sent = await sendTestNotification()
      setTestMessage(
        sent > 0
          ? `${sent}대 기기로 테스트 알림을 보냈습니다. 잠시 뒤 도착합니다.`
          : '알림을 보내지 못했습니다. 이 기기의 알림을 껐다 다시 켜 보세요.',
      )
    } catch (e) {
      setTestMessage(
        toErrorMessage(e, {
          byStatus: { 503: '서버에 푸시 알림이 설정되지 않았습니다.' },
        }),
      )
    } finally {
      setTesting(false)
    }
  }

  return (
    <StyleScope>
      {
        <div className={styles.panel}>
          <section aria-labelledby="device-title" className={styles.card}>
            <h2 id="device-title" className={styles.heading}>
              이 기기에서 알림 받기
            </h2>
            <DeviceNotice
              enabled={enabled}
              support={device.support}
              permission={device.permission}
              subscribed={device.subscribed}
            />
            <div className={styles.actions}>
              {device.subscribed ? (
                <Button
                  variant="secondary"
                  onClick={() => void device.disable()}
                  disabled={device.busy}
                >
                  이 기기 알림 끄기
                </Button>
              ) : (
                <Button
                  onClick={() => void device.enable()}
                  disabled={
                    device.busy ||
                    !enabled ||
                    device.support !== 'supported' ||
                    device.permission === 'denied'
                  }
                >
                  이 기기에서 알림 받기
                </Button>
              )}
              <Button
                variant="secondary"
                onClick={() => void runTest()}
                disabled={testing || !enabled || !device.subscribed}
              >
                테스트 알림 보내기
              </Button>
            </div>
            {device.error && (
              <p role="alert" className={styles.error}>
                {device.error}
              </p>
            )}
            {testMessage && <p role="status">{testMessage}</p>}
          </section>

          <section aria-labelledby="kinds-title" className={styles.card}>
            <h2 id="kinds-title" className={styles.heading}>
              받을 알림 종류
            </h2>
            <p className={styles.hint}>켜고 끄면 바로 저장됩니다. 내 모든 기기에 적용됩니다.</p>
            <ul className={styles.kinds}>
              {KINDS.map((kind) => (
                <li key={kind.key}>
                  <label className={styles.kind}>
                    <input
                      type="checkbox"
                      checked={current[kind.key]}
                      onChange={(e) => toggle(kind.key, e.target.checked)}
                    />
                    <span>
                      <strong>{kind.label}</strong>
                      <span className={styles.help}>{kind.help}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            {save.isError && (
              <p role="alert" className={styles.error}>
                {toErrorMessage(save.error)}
              </p>
            )}
            {save.isSuccess && <p role="status">저장했습니다.</p>}
          </section>
        </div>
      }
    </StyleScope>
  )
}

function DeviceNotice({
  enabled,
  support,
  permission,
  subscribed,
}: {
  enabled: boolean
  support: ReturnType<typeof usePushDevice>['support']
  permission: NotificationPermission
  subscribed: boolean
}) {
  if (!enabled) {
    return (
      <StyleScope>
        {
          <p className={styles.notice}>
            이 서버에는 푸시 알림이 설정되어 있지 않아 지금은 쓸 수 없습니다. 관리자에게 문의해
            주세요.
          </p>
        }
      </StyleScope>
    )
  }
  if (support === 'ios-needs-install') {
    return (
      <StyleScope>
        {
          <p className={styles.warning}>
            아이폰·아이패드에서는 사파리 하단의 공유 버튼에서 &quot;홈 화면에 추가&quot;를 한 뒤, 홈
            화면의 아이콘으로 열어야 알림을 받을 수 있습니다(iOS 16.4 이상).
          </p>
        }
      </StyleScope>
    )
  }
  if (support === 'unsupported') {
    return (
      <StyleScope>
        {
          <p className={styles.warning}>
            이 브라우저는 푸시 알림을 지원하지 않습니다. 크롬·엣지·파이어폭스·사파리 최신 버전을
            이용해 주세요.
          </p>
        }
      </StyleScope>
    )
  }
  if (permission === 'denied') {
    return (
      <StyleScope>
        {
          <p className={styles.warning}>
            브라우저에서 이 사이트의 알림이 차단되어 있습니다. 주소창 왼쪽 자물쇠(또는 사이트
            설정)에서 알림을 &quot;허용&quot;으로 바꾼 뒤 이 페이지를 새로 고쳐 주세요.
          </p>
        }
      </StyleScope>
    )
  }
  return (
    <StyleScope>
      {
        <p className={styles.notice}>
          {subscribed
            ? '이 기기에서 알림을 받고 있습니다.'
            : '버튼을 누르면 브라우저가 알림 허용을 물어봅니다. 허용해 주세요.'}
        </p>
      }
    </StyleScope>
  )
}
