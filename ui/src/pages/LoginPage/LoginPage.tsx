import { Link } from 'react-router-dom'
import { LoginForm } from '@/features/login/index.ts'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  return (
    <div className={styles.page}>
      <h1>로그인</h1>
      <LoginForm />
      <p className={styles.switch}>
        계정이 없으신가요? <Link to="/signup">가입하기</Link>
      </p>
    </div>
  )
}
