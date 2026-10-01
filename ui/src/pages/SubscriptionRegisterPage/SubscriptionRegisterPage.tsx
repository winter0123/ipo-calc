import { SubscriptionRegisterForm } from '../../features/subscription-register/index.ts'
import styles from './SubscriptionRegisterPage.module.css'

export default function SubscriptionRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>청약 등록</h1>
      <SubscriptionRegisterForm />
    </div>
  )
}
