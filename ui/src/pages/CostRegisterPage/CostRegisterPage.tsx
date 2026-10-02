import styles from './CostRegisterPage.module.css'
import { CostRegisterForm } from '@/features/cost-register/index.ts'

export default function CostRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>기타 비용 등록</h1>
      <CostRegisterForm />
    </div>
  )
}
