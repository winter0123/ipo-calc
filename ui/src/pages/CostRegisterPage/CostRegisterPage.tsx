import styles from './CostRegisterPage.module.css'
import CostRegisterForm from '../../features/cost-register/CostRegisterForm/CostRegisterForm.tsx'

export default function CostRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>비용 등록</h1>
      <CostRegisterForm />
    </div>
  )
}
