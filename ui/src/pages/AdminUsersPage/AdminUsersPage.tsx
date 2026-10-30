import { AdminUserList } from '@/features/admin-users/index.ts'
import styles, { StyleScope } from './AdminUsersPage.styles.ts'

export default function AdminUsersPage() {
  return (
    <StyleScope>
      <div className={styles.container}>
        <h1 className={styles.title}>회원 관리</h1>
        <AdminUserList />
      </div>
    </StyleScope>
  )
}
