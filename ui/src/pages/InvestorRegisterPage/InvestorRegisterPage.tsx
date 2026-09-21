import InvestorRegisterForm from '../../features/investor-register/InvestorRegisterForm.tsx'
import styles from './InvestorRegisterPage.module.css'

export default function InvestorRegisterPage() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>투자자 등록</h1>
      <InvestorRegisterForm />
    </div>
  )
}
