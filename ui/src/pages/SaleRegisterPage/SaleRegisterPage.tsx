import styles from './SaleRegisterPage.module.css'
import { SaleRegisterForm } from '../../features/sale-register/index.ts'

export default function SaleRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>매도 등록</h1>
      <SaleRegisterForm />
    </div>
  )
}
