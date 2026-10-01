import { AdminAccountList } from '@/features/admin-accounts/index.ts'
import styles from './AdminAccountsPage.module.css'

export default function AdminAccountsPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>관리자</h1>
      <AdminAccountList />
    </div>
  )
}
