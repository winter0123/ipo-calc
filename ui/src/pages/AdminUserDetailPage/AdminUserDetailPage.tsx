import { AdminUserDetail } from '@/features/admin-users/index.ts'
import styles, { StyleScope } from './AdminUserDetailPage.styles.ts'

export default function AdminUserDetailPage() {
  return (
    <StyleScope>
      <div className={styles.container}>
        <h1 className={styles.title}>회원 상세</h1>
        <AdminUserDetail />
      </div>
    </StyleScope>
  )
}
