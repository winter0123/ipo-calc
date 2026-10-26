import { NotificationSettings } from '@/features/notification-settings/index.ts'
import styles, { StyleScope } from './NotificationsPage.styles.ts'

export default function NotificationsPage() {
  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>알림 설정</h1>
          <p>청약 마감일과 상장일 아침에 브라우저 알림으로 알려 드립니다. 기기마다 따로 켭니다.</p>
          <NotificationSettings />
        </div>
      }
    </StyleScope>
  )
}
