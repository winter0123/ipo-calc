import { SignupForm } from '../../features/signup/index.ts'
import styles from './SignupPage.module.css'

export default function SignupPage() {
  return (
    <div className={styles.page}>
      <h1>회원가입</h1>
      <SignupForm />
    </div>
  )
}
