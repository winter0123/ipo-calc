import { SecuritiesAccountRegisterForm } from '../../features/securities-account-register/index.ts'
import styles from './SecuritiesAccountRegisterPage.module.css'

export default function SecuritiesAccountRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>증권계좌 등록</h1>
      <SecuritiesAccountRegisterForm />
    </div>
  )
}
