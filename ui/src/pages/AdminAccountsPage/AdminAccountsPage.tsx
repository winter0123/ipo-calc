import { AdminAccountList } from '@/features/admin-accounts/index.ts'
import styles from './AdminAccountsPage.module.css'

export default function AdminAccountsPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>가입 승인 관리</h1>
      <AdminAccountList />
    </div>
  )
}
