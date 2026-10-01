import { LoginForm } from '../../features/login/index.ts'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  return (
    <div className={styles.page}>
      <h1>로그인</h1>
      <LoginForm />
    </div>
  )
}
