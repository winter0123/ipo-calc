import { AdminAccountList } from '@/features/admin-accounts/index.ts'
import styles, { StyleScope } from './AdminAccountsPage.styles.ts'

export default function AdminAccountsPage() {
  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>가입 승인 관리</h1>
          <AdminAccountList />
        </div>
      }
    </StyleScope>
  )
}
